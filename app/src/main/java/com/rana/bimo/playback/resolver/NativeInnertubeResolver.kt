package com.rana.bimo.playback.resolver

import android.net.ConnectivityManager
import com.rana.bimo.constants.AudioQuality
import com.rana.bimo.utils.YTPlayerUtils
import com.zionhuang.innertube.YouTube

class NativeInnertubeResolver : PlaybackResolver {
    override suspend fun resolve(
        songId: String,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager
    ): PlaybackSource {
        val playbackData = YTPlayerUtils.playerResponseForPlayback(
            songId,
            audioQuality = audioQuality,
            connectivityManager = connectivityManager
        ).getOrThrow()

        val extraHeaders = mutableMapOf("User-Agent" to playbackData.streamUserAgent)
        YouTube.visitorData?.let { visitorData ->
            extraHeaders["X-Goog-Visitor-Id"] = visitorData
        }

        return PlaybackSource(
            url = playbackData.streamUrl,
            mimeType = playbackData.format.mimeType.split(";")[0],
            headers = extraHeaders,
            expiresAt = System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L),
            duration = playbackData.videoDetails?.lengthSeconds?.toInt() ?: -1,
            isLocal = false,
            originalPlaybackData = playbackData
        )
    }
}
