/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.rana.bimo.utils

import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import androidx.media3.common.PlaybackException
import com.rana.bimo.constants.AudioQuality
import com.rana.bimo.utils.YTPlayerUtils.MAIN_CLIENT
import com.rana.bimo.utils.YTPlayerUtils.STREAM_FALLBACK_CLIENTS
import com.rana.bimo.utils.YTPlayerUtils.validateStatus
import com.rana.bimo.utils.potoken.PoTokenGenerator
import com.rana.bimo.utils.potoken.PoTokenResult
import com.zionhuang.innertube.NewPipeUtils
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeClient
import com.zionhuang.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.zionhuang.innertube.models.YouTubeClient.Companion.WEB
import com.zionhuang.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.zionhuang.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.zionhuang.innertube.models.response.PlayerResponse
import okhttp3.OkHttpClient

object YTPlayerUtils {

    private const val TAG = "YTPlayerUtils"

    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    /**
     * The main client is used for metadata and initial streams.
     * Do not use other clients for this because it can result in inconsistent metadata.
     * For example other clients can have different normalization targets (loudnessDb).
     *
     * [com.zionhuang.innertube.models.YouTubeClient.WEB_REMIX] is preferred because it provides:
     * - the correct metadata (like loudnessDb)
     * - premium formats
     */
    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    /**
     * Clients used for fallback streams in case the streams of the main client do not work.
     * Ordered by reliability: auth-free mobile clients first, then web clients.
     * - IOS: reliable, no PoToken needed, no login required
     * - ANDROID: reliable, no PoToken needed, loginSupported but not required
     * - ANDROID_VR_NO_AUTH: no auth at all, historically very reliable
     * - WEB: needs PoToken but no login required
     * - WEB_CREATOR/TVHTML5: need login, will be skipped if not logged in
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        YouTubeClient.IOS,
        YouTubeClient.ANDROID,
        YouTubeClient.ANDROID_VR_NO_AUTH,
        WEB,
        WEB_CREATOR,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
    )

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamUserAgent: String,
        val streamExpiresInSeconds: Int,
    )

    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [MAIN_CLIENT].
     * Format & stream can be from [MAIN_CLIENT] or [STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> = runCatching {
        Log.d(TAG, "Playback info requested: $videoId")

        /**
         * This is required for some clients to get working streams however
         * it should not be forced for the [MAIN_CLIENT] because the response of the [MAIN_CLIENT]
         * is required even if the streams won't work from this client.
         * This is why it is allowed to be null.
         */
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)

        val isLoggedIn = YouTube.cookie != null
        val sessionId =
            if (isLoggedIn) {
                // signed in sessions use dataSyncId as identifier
                YouTube.dataSyncId
            } else {
                // signed out sessions use visitorData as identifier
                YouTube.visitorData
            }

        Log.d(TAG, "[$videoId] signatureTimestamp: $signatureTimestamp, isLoggedIn: $isLoggedIn")

        val (webPlayerPot, webStreamingPot) = getWebClientPoTokenOrNull(videoId, sessionId)?.let {
            Pair(it.playerRequestPoToken, it.streamingDataPoToken)
        } ?: Pair(null, null).also {
            Log.w(TAG, "[$videoId] No po token")
        }

        val mainPlayerResponse =
            YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp, webPlayerPot)
                .getOrThrow()

        val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking

        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamUserAgent: String? = null
        var streamExpiresInSeconds: Int? = null

        // Tracks the best result we found so far (even if validation failed).
        // Used as a last resort if all remaining clients are skipped or also fail.
        var bestFallbackFormat: PlayerResponse.StreamingData.Format? = null
        var bestFallbackUrl: String? = null
        var bestFallbackUserAgent: String? = null
        var bestFallbackExpiresIn: Int? = null
        var bestFallbackPlayerResponse: PlayerResponse? = null

        var streamPlayerResponse: PlayerResponse? = null
        for (clientIndex in (-1 until STREAM_FALLBACK_CLIENTS.size)) {
            // decide which client to use for streams and load its player response
            val client: YouTubeClient
            if (clientIndex == -1) {
                Log.d(TAG, "DIAGNOSTIC: Trying MAIN_CLIENT: ${MAIN_CLIENT.clientName} (v${MAIN_CLIENT.clientVersion})")
                // try with streams from main client first
                client = MAIN_CLIENT
                streamPlayerResponse = mainPlayerResponse
            } else {
                val fallbackClient = STREAM_FALLBACK_CLIENTS[clientIndex]
                Log.d(TAG, "DIAGNOSTIC: Trying FALLBACK_CLIENT: ${fallbackClient.clientName} (v${fallbackClient.clientVersion})")
                // after main client use fallback clients
                client = fallbackClient

                if (client.loginRequired && !isLoggedIn) {
                    // skip client if it requires login but user is not logged in
                    Log.d(TAG, "DIAGNOSTIC: [${client.clientName}] Skipping (loginRequired, not logged in)")
                    continue
                }

                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, signatureTimestamp, webPlayerPot)
                        .getOrNull()
            }

            // reset per-client stream fields
            format = null
            streamUrl = null
            streamUserAgent = null
            streamExpiresInSeconds = null

            Log.d(TAG, "DIAGNOSTIC: [$videoId] client: ${client.clientName} -> playabilityStatus: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")

            // process current client response
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                val audioFormats = streamPlayerResponse.streamingData?.adaptiveFormats?.filter { it.isAudio }
                Log.d(TAG, "DIAGNOSTIC: [${client.clientName}] Found ${audioFormats?.size ?: 0} audio formats")
                format =
                    findFormat(
                        streamPlayerResponse,
                        audioQuality,
                        connectivityManager,
                    ) ?: continue
                streamUrl = findUrlOrNull(format, videoId) ?: continue
                streamUserAgent = client.userAgent
                streamExpiresInSeconds =
                    streamPlayerResponse.streamingData?.expiresInSeconds ?: continue

                Log.d(TAG, "DIAGNOSTIC: [${client.clientName}] Selected format: itag=${format.itag}, mimeType=${format.mimeType}, bitrate=${format.bitrate}")
                Log.d(TAG, "DIAGNOSTIC: [${client.clientName}] Stream URL host: ${runCatching { Uri.parse(streamUrl).host }.getOrDefault("unknown")}")

                if (client.useWebPoTokens && webStreamingPot != null) {
                    streamUrl += "&pot=$webStreamingPot";
                }

                // Save as best fallback regardless of validation outcome
                if (bestFallbackUrl == null) {
                    bestFallbackFormat = format
                    bestFallbackUrl = streamUrl
                    bestFallbackUserAgent = streamUserAgent
                    bestFallbackExpiresIn = streamExpiresInSeconds
                    bestFallbackPlayerResponse = streamPlayerResponse
                }

                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1) {
                    /** skip [validateStatus] for last client */
                    Log.d(TAG, "DIAGNOSTIC: [${client.clientName}] Last fallback client, skipping validation")
                    break
                }
                
                Log.d(TAG, "DIAGNOSTIC: [${client.clientName}] Validating stream URL...")
                if (validateStatus(streamUrl, client.userAgent)) {
                    // working stream found
                    Log.i(TAG, "DIAGNOSTIC: [$videoId] [${client.clientName}] found working stream")
                    break
                } else {
                    Log.w(TAG, "DIAGNOSTIC: [$videoId] [${client.clientName}] got bad http status code, trying next client")
                }
            }
        }

        // If the loop ended without a validated stream but we have a best-effort fallback, use it
        if (streamUrl == null && bestFallbackUrl != null) {
            Log.w(TAG, "[$videoId] No validated stream found; using best-effort fallback (may 403)")
            format = bestFallbackFormat
            streamUrl = bestFallbackUrl
            streamUserAgent = bestFallbackUserAgent
            streamExpiresInSeconds = bestFallbackExpiresIn
            streamPlayerResponse = bestFallbackPlayerResponse
        }

        if (streamPlayerResponse == null) {
            throw Exception("Bad stream player response")
        }
        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            throw PlaybackException(
                streamPlayerResponse.playabilityStatus.reason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }
        if (streamExpiresInSeconds == null) {
            throw Exception("Missing stream expire time")
        }
        if (format == null) {
            throw Exception("Could not find format")
        }
        if (streamUrl == null) {
            throw Exception("Could not find stream url")
        }
        if (streamUserAgent == null) {
            throw Exception("Missing stream user agent")
        }

        Log.d(TAG, "[$videoId] stream url domain: ${runCatching { Uri.parse(streamUrl).host }.getOrDefault("unknown")}")
        Log.d(TAG, "[$videoId] format: mime=${format.mimeType}, bitrate=${format.bitrate}, contentLength=${format.contentLength}")
        Log.d(TAG, "[$videoId] stream expires in: ${streamExpiresInSeconds}s")

        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamUserAgent,
            streamExpiresInSeconds,
        )
    }

    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> =
        YouTube.player(videoId, playlistId, client = MAIN_CLIENT)

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? =
        playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio }
            ?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) -1 else 1
                    AudioQuality.HIGH -> 1
                    AudioQuality.LOW -> -1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0) // prefer opus stream
            }

    /**
     * Checks if the stream url returns a successful status.
     * Performs a two-step validation (bytes=0-1, then bytes=2-3) to ensure
     * the stream is fully authorized for subsequent chunks (not just the initial HEAD-like check).
     */
    private fun validateStatus(url: String, userAgent: String): Boolean {
        try {
            val reqBuilder = { range: String ->
                okhttp3.Request.Builder()
                    .get()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Range", range)
                    .apply {
                        YouTube.visitorData?.let { visitorData ->
                            header("X-Goog-Visitor-Id", visitorData)
                        }
                    }
                    .build()
            }
            
            // First validation chunk - check initial access
            val res1 = httpClient.newCall(reqBuilder("bytes=0-1")).execute()
            val code1 = res1.code
            res1.body?.close()
            
            if (!res1.isSuccessful) {
                Log.w(TAG, "validateStatus: Chunk 1 failed with HTTP $code1")
                return false
            }
            
            // Second validation chunk - simulate ExoPlayer's sequential chunk fetch
            // Some CDNs authenticate only the first byte range and reject subsequent ones
            val res2 = httpClient.newCall(reqBuilder("bytes=2-3")).execute()
            val code2 = res2.code
            res2.body?.close()
            
            if (!res2.isSuccessful) {
                Log.e(TAG, "validateStatus: CDN AUTHORIZATION FAILED! Chunk 1=$code1 but Chunk 2=$code2")
                return false
            }

            Log.d(TAG, "validateStatus: Stream validated OK ($code1 / $code2)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "validateStatus: Exception during validation", e)
        }
        return false
    }

    /**
     * Wrapper around the [NewPipeUtils.getSignatureTimestamp] function which reports exceptions
     */
    private fun getSignatureTimestampOrNull(
        videoId: String
    ): Int? {
        return NewPipeUtils.getSignatureTimestamp(videoId)
            .onFailure {
                reportException(it)
            }
            .getOrNull()
    }

    /**
     * Wrapper around the [NewPipeUtils.getStreamUrl] function which reports exceptions
     */
    private fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String
    ): String? {
        return NewPipeUtils.getStreamUrl(format, videoId)
            .onFailure {
                reportException(it)
            }
            .getOrNull()
    }

    /**
     * Wrapper around the [PoTokenGenerator.getWebClientPoToken] function which reports exceptions
     */
    private fun getWebClientPoTokenOrNull(videoId: String, sessionId: String?): PoTokenResult? {
        if (sessionId == null) {
            Log.d(TAG, "[$videoId] Session identifier is null")
            return null
        }
        try {
            return poTokenGenerator.getWebClientPoToken(videoId, sessionId)
        } catch (e: Exception) {
            reportException(e)
        }
        return null
    }
}
