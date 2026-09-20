package com.rana.bimo.playback.resolver

import android.net.ConnectivityManager
import androidx.media3.datasource.DataSpec
import com.rana.bimo.constants.AudioQuality

data class PlaybackSource(
    val url: String,
    val mimeType: String,
    val headers: Map<String, String>,
    val expiresAt: Long,
    val duration: Int,
    val isLocal: Boolean = false,
    val isExternalProvider: Boolean = false,
    val externalProviderId: String? = null,
    val originalPlaybackData: com.rana.bimo.utils.YTPlayerUtils.PlaybackData? = null
) {
    fun toDataSpec(originalSpec: DataSpec): DataSpec {
        val specBuilder = originalSpec.buildUpon().setUri(url)
        val specHeaders = mutableMapOf<String, String>()
        specHeaders.putAll(originalSpec.httpRequestHeaders)
        specHeaders.putAll(headers)
        
        // Ensure range request for ExoPlayer chunking is preserved if it's not a local file
        if (!isLocal) {
            if (originalSpec.position == 0L && originalSpec.length == androidx.media3.common.C.LENGTH_UNSET.toLong()) {
                specHeaders["Range"] = "bytes=0-"
            }
        }
        
        return specBuilder.setHttpRequestHeaders(specHeaders).build()
    }
}

interface PlaybackResolver {
    suspend fun resolve(
        songId: String,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager
    ): PlaybackSource
}
