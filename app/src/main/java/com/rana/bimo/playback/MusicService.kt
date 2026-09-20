/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.rana.bimo.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.media.audiofx.AudioEffect
import android.net.ConnectivityManager
import android.os.Binder
import android.util.Log
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
import androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioOffloadSupportProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.rana.bimo.MainActivity
import com.rana.bimo.R
import com.rana.bimo.constants.AudioNormalizationKey
import com.rana.bimo.constants.AudioOffload
import com.rana.bimo.constants.AudioQuality
import com.rana.bimo.constants.AudioQualityKey
import com.rana.bimo.constants.AutoLoadMoreKey
import com.rana.bimo.constants.KeepAliveKey
import com.rana.bimo.constants.LastPosKey
import com.rana.bimo.constants.MediaSessionConstants.CommandToggleLike
import com.rana.bimo.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.rana.bimo.constants.MediaSessionConstants.CommandToggleShuffle
import com.rana.bimo.constants.MediaSessionConstants.CommandToggleStartRadio
import com.rana.bimo.constants.PauseListenHistoryKey
import com.rana.bimo.constants.PauseRemoteListenHistoryKey
import com.rana.bimo.constants.PersistentQueueKey
import com.rana.bimo.constants.PlayerVolumeKey
import com.rana.bimo.constants.RepeatModeKey
import com.rana.bimo.constants.ShowLyricsKey
import com.rana.bimo.constants.SkipOnErrorKey
import com.rana.bimo.constants.SkipSilenceKey
import com.rana.bimo.constants.minPlaybackDurKey
import com.rana.bimo.db.MusicDatabase
import com.rana.bimo.db.entities.Event
import com.rana.bimo.db.entities.FormatEntity
import com.rana.bimo.db.entities.RelatedSongMap
import com.rana.bimo.di.AppModule.PlayerCache
import com.rana.bimo.di.DownloadCache
import com.rana.bimo.extensions.SilentHandler
import com.rana.bimo.extensions.collect
import com.rana.bimo.extensions.collectLatest
import com.rana.bimo.extensions.currentMetadata
import com.rana.bimo.extensions.findNextMediaItemById
import com.rana.bimo.extensions.metadata
import com.rana.bimo.extensions.setOffloadEnabled
import com.rana.bimo.lyrics.LyricsHelper
import com.rana.bimo.models.HybridCacheDataSinkFactory
import com.rana.bimo.models.MediaMetadata
import com.rana.bimo.models.MultiQueueObject
import com.rana.bimo.models.toMediaMetadata
import com.rana.bimo.playback.queues.ListQueue
import com.rana.bimo.playback.queues.Queue
import com.rana.bimo.playback.queues.YouTubeQueue
import com.rana.bimo.utils.CoilBitmapLoader
import com.rana.bimo.utils.NetworkConnectivityObserver
import com.rana.bimo.utils.SyncUtils
import com.rana.bimo.utils.YTPlayerUtils
import com.rana.bimo.utils.dataStore
import com.rana.bimo.utils.enumPreference
import com.rana.bimo.utils.get
import com.rana.bimo.utils.reportException
import com.google.common.util.concurrent.MoreExecutors
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.WatchEndpoint
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow

const val MAX_CONSECUTIVE_ERR = 3

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@AndroidEntryPoint
class MusicService : MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    val TAG = MusicService::class.simpleName.toString()

    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    private val scope = CoroutineScope(Dispatchers.Main)
    private val binder = MusicBinder()

    private lateinit var connectivityManager: ConnectivityManager

    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(true)

    private val audioQuality by enumPreference(this, AudioQualityKey, AudioQuality.AUTO)

    var queueBoard = QueueBoard(this)
    var queueTitle: String? = null
    var queuePlaylistId: String? = null
    private var lastMediaItemIndex = -1

    val currentMediaMetadata = MutableStateFlow<com.rana.bimo.models.MediaMetadata?>(null)

    private val currentSong = currentMediaMetadata.flatMapLatest { mediaMetadata ->
        database.song(mediaMetadata?.id)
    }.stateIn(scope, SharingStarted.Lazily, null)

    private val currentFormat = currentMediaMetadata.flatMapLatest { mediaMetadata ->
        database.format(mediaMetadata?.id)
    }

    private val normalizeFactor = MutableStateFlow(1f)
    val playerVolume = MutableStateFlow(dataStore.get(PlayerVolumeKey, 1f).coerceIn(0f, 1f))

    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache

    lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession

    private var isAudioEffectSessionOpened = false

    var consecutivePlaybackErr = 0
    private val songUrlCache = HashMap<String, Triple<String, Long, String>>()

    override fun onCreate() {
        super.onCreate()

        // network connectivity
        try {
            connectivityObserver.unregister()
        } catch (e: UninitializedPropertyAccessException) {
            // lol
        }
        connectivityObserver = NetworkConnectivityObserver(this)

        scope.launch {
            connectivityObserver.networkStatus.collect { isConnected ->
                isNetworkConnected.value = isConnected

                if (isConnected && waitingForNetworkConnection.value) {
                    waitingForNetworkConnection.value = false
                    player.prepare()
                    player.play()
                }
            }
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(createDataSourceFactory()))
            .setRenderersFactory(createRenderersFactory())
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()
            .apply {
                // listeners
                addListener(this@MusicService)
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)

                        Log.e(TAG, "================ EXOPLAYER ERROR ================")
                        Log.e(TAG, "errorCode: ${error.errorCode}")
                        Log.e(TAG, "errorCodeName: ${error.errorCodeName}")
                        Log.e(TAG, "message: ${error.message}")
                        
                        var cause: Throwable? = error.cause
                        var depth = 1
                        var httpException: androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException? = null
                        
                        while (cause != null && depth <= 5) {
                            Log.e(TAG, "cause[$depth]: ${cause.javaClass.simpleName}: ${cause.message}")
                            if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                                httpException = cause
                            }
                            cause = cause.cause
                            depth++
                        }

                        if (httpException != null) {
                            Log.e(TAG, "--- HTTP ERROR DETAILS ---")
                            Log.e(TAG, "responseCode: ${httpException.responseCode}")
                            
                            val dataSpec = httpException.dataSpec
                            Log.e(TAG, "dataSpec.uri HOST: ${dataSpec.uri.host}")
                            
                            val safeReqHeaders = dataSpec.httpRequestHeaders.mapValues { (k, v) ->
                                if (k.equals("Authorization", true) || k.equals("Cookie", true) || 
                                    v.contains("pot=") || v.contains("signature=")) "<redacted>" else v
                            }
                            Log.e(TAG, "dataSpec.httpRequestHeaders: $safeReqHeaders")
                            
                            val safeResHeaders = httpException.headerFields.mapValues { (k, v) ->
                                if (k.equals("Set-Cookie", true)) listOf("<redacted>") else v
                            }
                            Log.e(TAG, "response headers: $safeResHeaders")
                        }
                        Log.e(TAG, "=================================================")

                        // wait for reconnection
                        val isConnectionError = (error.cause?.cause is PlaybackException)
                                && (error.cause?.cause as PlaybackException).errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                        if (!isNetworkConnected.value || isConnectionError) {
                            waitOnNetworkError()
                            return
                        }

                        // Invalidate cache on HTTP 403 to force a fresh URL on next attempt
                        val isHttpError = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                        val is403 = error.cause?.message?.contains("403") == true || error.message?.contains("403") == true
                        if (isHttpError || is403) {
                            Log.e(TAG, "DIAGNOSTIC: ExoPlayer encountered HTTP 403. Invalidating URL cache for current song.")
                            val currentMediaId = player.currentMediaItem?.mediaId
                            if (currentMediaId != null) {
                                songUrlCache.remove(currentMediaId)
                            }

                            if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR) {
                                consecutivePlaybackErr++
                                player.prepare()
                                player.play()
                                return
                            }
                        }

                        if (dataStore.get(SkipOnErrorKey, false)) {
                            skipOnError()
                        } else {
                            stopOnError()
                        }

                        Toast.makeText(
                            this@MusicService,
                            "${getString(R.string.err_general)}: ${error.message} (${error.errorCode}): ${error.cause?.message ?: ""} ",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    // start playback again on seek
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        super.onMediaItemTransition(mediaItem, reason)
                        // +2 when and error happens, and -1 when transition. Thus when error, number increments by 1, else doesn't change
                        if (consecutivePlaybackErr > 0) {
                            consecutivePlaybackErr--
                        }

                        if (player.isPlaying && reason == MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                            player.prepare()
                            player.play()
                        }

                        // Auto load more songs
                        val q = queueBoard.getCurrentQueue()
                        val songId = q?.playlistId
                        if (dataStore.get(AutoLoadMoreKey, true) &&
                            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
                            player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
                            songId != null // aka "hasNext"
                        ) {
                            scope.launch(SilentHandler) {
                                val mediaItems = YouTubeQueue(WatchEndpoint(songId)).nextPage()
                                if (player.playbackState != STATE_IDLE) {
                                    queueBoard.enqueueEnd(mediaItems.drop(1), isRadio = true)
                                }
                            }
                        }

                        // this absolute eye sore detects if we loop back to the beginning of queue, when shuffle AND repeat all
                        // no, when repeat mode is on, player does not "STATE_ENDED"
                        if (player.currentMediaItemIndex == 0 && lastMediaItemIndex == player.mediaItemCount - 1 &&
                            (reason == MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == MEDIA_ITEM_TRANSITION_REASON_SEEK) &&
                            player.shuffleModeEnabled && player.repeatMode == REPEAT_MODE_ALL
                        ) {
                            queueBoard.shuffleCurrent(false) // reshuffle queue
                            queueBoard.setCurrQueue()
                        }
                        lastMediaItemIndex = player.currentMediaItemIndex

                        updateNotification() // also updates when queue changes

                        queueBoard.setCurrQueuePosIndex(player.currentMediaItemIndex)
                    }
                })
                sleepTimer = SleepTimer(scope, this)
                addListener(sleepTimer)
                addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))

                // misc
                setOffloadEnabled(dataStore.get(AudioOffload, false))
            }

        mediaLibrarySessionCallback.apply {
            service = this@MusicService
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
        }

        mediaSession = MediaLibrarySession.Builder(this, player, mediaLibrarySessionCallback)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setBitmapLoader(CoilBitmapLoader(this, scope))
            .build()

        player.repeatMode = dataStore.get(RepeatModeKey, REPEAT_MODE_OFF)

        // Keep a connected controller so that notification works
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        connectivityManager = getSystemService()!!

        combine(playerVolume, normalizeFactor) { playerVolume, normalizeFactor ->
            playerVolume * normalizeFactor
        }.collectLatest(scope) {
            player.volume = it
        }

        playerVolume.debounce(1000).collect(scope) { volume ->
            dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        currentSong.collect(scope) {
            updateNotification()
        }

        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged()
        ) { mediaMetadata, showLyrics ->
            mediaMetadata to showLyrics
        }

        dataStore.data
            .map { it[SkipSilenceKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                player.skipSilenceEnabled = it
            }

        combine(
            currentFormat,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged()
        ) { format, normalizeAudio ->
            format to normalizeAudio
        }.collectLatest(scope) { (format, normalizeAudio) ->
            normalizeFactor.value = if (normalizeAudio && format?.loudnessDb != null) {
                min(10f.pow(-format.loudnessDb.toFloat() / 20), 1f)
            } else {
                1f
            }
        }

        initQueue()
        CoroutineScope(Dispatchers.Main).launch {
            val queuePos = queueBoard.setCurrQueue(false)
            if (queuePos != null) {
                player.seekTo(queuePos, dataStore.get(LastPosKey, C.TIME_UNSET))
                dataStore.edit { settings ->
                    settings[LastPosKey] = C.TIME_UNSET
                }
            }
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this, { NOTIFICATION_ID }, CHANNEL_ID, R.string.music_player)
                .apply {
                    setSmallIcon(R.drawable.small_icon)
                }
        )
    }

    fun waitOnNetworkError() {
        waitingForNetworkConnection.value = true
        Toast.makeText(this@MusicService, getString(R.string.wait_to_reconnect), Toast.LENGTH_LONG).show()
    }

    fun skipOnError() {
        /**
         * Auto skip to the next media item on error.
         *
         * To prevent a "runaway diesel engine" scenario, force the user to take action after
         * too many errors come up too quickly. Pause to show player "stopped" state
         */
        consecutivePlaybackErr += 2
        val nextWindowIndex = player.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            player.seekTo(nextWindowIndex, C.TIME_UNSET)
            player.prepare()
            player.play()

            Toast.makeText(this@MusicService, getString(R.string.err_play_next_on_error), Toast.LENGTH_SHORT).show()
            return
        }

        player.pause()
        Toast.makeText(this@MusicService, getString(R.string.err_stop_on_too_many_errors), Toast.LENGTH_LONG).show()
        consecutivePlaybackErr = 0
    }

    fun stopOnError() {
        player.pause()
        Toast.makeText(this@MusicService, getString(R.string.err_stop_on_error), Toast.LENGTH_LONG).show()
    }

    fun initQueue() {
        if (dataStore.get(PersistentQueueKey, true)) {
            queueBoard = QueueBoard(this, database.readQueue().toMutableList())
            queueBoard.getCurrentQueue()?.let {
                player.shuffleModeEnabled = it.shuffled
                queueBoard.initialized = true
            }
        } else {
            queueBoard = QueueBoard(this)
        }
    }

    fun deInitQueue() {
        queueBoard.shutdown()
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
        // do not replace the object. Can lead to entire queue being deleted even though it is supposed to be saved already
        queueBoard.initialized = false
    }

    fun updateNotification() {
        mediaSession.setCustomLayout(
            listOf(
                CommandButton.Builder(if (queueBoard.getCurrentQueue()?.shuffled == true) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
                    .setDisplayName(getString(if (queueBoard.getCurrentQueue()?.shuffled == true) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setSessionCommand(CommandToggleShuffle)
                    .build(),
                CommandButton.Builder(
                    when (player.repeatMode) {
                        REPEAT_MODE_OFF -> CommandButton.ICON_REPEAT_OFF
                        REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                        REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                        else -> throw IllegalStateException()
                    }
                )
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> throw IllegalStateException()
                            }
                        )
                    )
                    .setSessionCommand(CommandToggleRepeatMode)
                    .build(),
                CommandButton.Builder(if (currentSong.value?.song?.liked == true) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                    .setDisplayName(getString(if (currentSong.value?.song?.liked == true) R.string.action_remove_like else R.string.action_like))
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_RADIO)
                    .setDisplayName(getString(R.string.start_radio))
                    .setSessionCommand(CommandToggleStartRadio)
                    .setEnabled(currentSong.value != null)
                    .build()
            )
        )
    }

    private suspend fun recoverSong(mediaId: String, playbackData: YTPlayerUtils.PlaybackData? = null) {
        val song = database.song(mediaId).first()
        val mediaMetadata = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: (playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId)
                .getOrNull()?.videoDetails)?.lengthSeconds?.toInt()
            ?: -1
        database.query {
            if (song == null) insert(mediaMetadata.copy(duration = duration))
            else if (song.song.duration == -1) update(song.song.copy(duration = duration))
        }
        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint = YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id
                        )
                    }
                    .forEach(::insert)
            }
        }
    }

    /**
     * Play a queue.
     *
     * @param queue Queue to play.
     * @param playWhenReady
     * @param replace Replace media items instead of the underlying logic
     * @param title Title override for the queue. If this value us unspecified, this method takes the value from queue.
     * If both are unspecified, the title will default to "Queue".
     */
    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
        replace: Boolean = false,
        isRadio: Boolean = false,
        title: String? = null
    ) {
        if (!queueBoard.initialized) {
            initQueue()
            queueBoard.initialized = true
        }

        var queueTitle = title
        queuePlaylistId = queue.playlistId
        var q: MultiQueueObject? = null
        val preloadItem = queue.preloadItem

        scope.launch {
            try {
                if (preloadItem != null) {
                    q = queueBoard.addQueue(
                        queueTitle ?: "Radio\u2060temp",
                        listOf(preloadItem),
                        shuffled = queue.startShuffled,
                        replace = replace,
                        isRadio = isRadio
                    )
                    queueBoard.setCurrQueue(q)
                }

                val initialStatus = withContext(Dispatchers.IO) { queue.getInitialStatus() }
                // do not find a title if an override is provided
                if ((title == null) && initialStatus.title != null) {
                    queueTitle = initialStatus.title

                    if (preloadItem != null && q != null) {
                        queueBoard.renameQueue(q!!, queueTitle)
                    }
                }

                val items = ArrayList<MediaMetadata>()
                if (initialStatus.items.isEmpty()) return@launch
                if (preloadItem != null) {
                    items.add(preloadItem)
                    items.addAll(initialStatus.items.subList(1, initialStatus.items.size))
                } else {
                    items.addAll(initialStatus.items)
                }
                val q = queueBoard.addQueue(
                    queueTitle ?: getString(R.string.queue),
                    items,
                    shuffled = queue.startShuffled,
                    startIndex = if (initialStatus.mediaItemIndex > 0) initialStatus.mediaItemIndex else 0,
                    replace = replace || preloadItem != null,
                    isRadio = isRadio
                )
                queueBoard.setCurrQueue(q)

                player.prepare()
                player.playWhenReady = playWhenReady
            } catch (e: Exception) {
                reportException(e)
                Toast.makeText(this@MusicService, "${getString(R.string.err_general)}: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    /**
     * Add items to queue, right after current playing item
     */
    fun enqueueNext(items: List<MediaItem>) {
        if (!queueBoard.initialized) {

            // when enqueuing next when player isn't active, play as a new song
            if (items.isNotEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    playQueue(
                        ListQueue(
                            title = items.first().mediaMetadata.title.toString(),
                            items = items.mapNotNull { it.metadata }
                        )
                    )
                }
            }
        } else {
            // enqueue next
            queueBoard.getCurrentQueue()?.let {
                queueBoard.addSongsToQueue(it, player.currentMediaItemIndex + 1, items.mapNotNull { it.metadata })
            }
        }
    }


    /**
     * Add items to end of current queue
     */
    fun enqueueEnd(items: List<MediaItem>) {
        queueBoard.enqueueEnd(items.mapNotNull { it.metadata })
    }

    fun toggleLibrary() {
        database.query {
            currentSong.value?.let {
                update(it.song.toggleLibrary())
            }
        }
    }

    fun toggleLike() {
        database.query {
            currentSong.value?.let {
                val song = it.song.toggleLike()
                update(song)

                if (!song.isLocal) {
                    syncUtils.likeSong(song)
                }
                downloadUtil.autoDownloadIfLiked(song)
            }
        }
    }

    fun toggleStartRadio() {
        val mediaMetadata = player.currentMetadata ?: return
        playQueue(YouTubeQueue.radio(mediaMetadata), isRadio = true)
    }

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = true
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            }
        )
    }

    override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
        if (playbackState == STATE_IDLE) {
            queuePlaylistId = null
            queueTitle = null
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                openAudioEffectSession()
            } else {
                closeAudioEffectSession()
                if (!player.playWhenReady) {
                    waitingForNetworkConnection.value = false
                }
            }
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }
    }


    private fun createCacheDataSource(): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource.Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            this,
                            OkHttpDataSource.Factory(
                                OkHttpClient.Builder()
                                    .proxy(YouTube.proxy)
                                    .addInterceptor { chain ->
                                        val request = chain.request()
                                        val response = chain.proceed(request)
                                        var redirectCount = 0
                                        var prior = response.priorResponse
                                        while (prior != null) {
                                            redirectCount++
                                            prior = prior.priorResponse
                                        }
                                        Log.d(
                                            TAG,
                                            "CDN ${request.method} ${request.url.host}${request.url.encodedPath} " +
                                                    "range=${request.header("Range") ?: "none"} " +
                                                    "ua=${request.header("User-Agent")?.take(32) ?: "none"} " +
                                                    "params=${request.url.queryParameterNames.joinToString(",")} " +
                                                    "status=${response.code} redirects=$redirectCount"
                                        )
                                        response
                                    }
                                    .build()
                            )
                        )
                    )
                    .setCacheWriteDataSinkFactory(
                        HybridCacheDataSinkFactory(playerCache) { dataSpec ->
                            val isLocal = queueBoard.getCurrentQueue()?.findSong(dataSpec.key ?: "")?.isLocal == true
                            Log.d(TAG, "SONG CACHE: ${!isLocal}")
                            !isLocal
                        }
                    )
                    .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)
            )
            .setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")
            Log.d(TAG, "PLAYING: song id = $mediaId")

            val song = queueBoard.getCurrentQueue()?.findSong(dataSpec.key ?: "")
            // local song
            if (song?.localPath != null) {
                if (song.isLocal) {
                    Log.d(TAG, "PLAYING: local song")
                    val file = File(song.localPath)
                    if (!file.exists()) {
                        throw PlaybackException(
                            "File not found",
                            Throwable(),
                            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                        )
                    }

                    return@Factory dataSpec.withUri(file.toUri())
                } else {
                    val isDownloadNew = downloadUtil.localMgr.getFilePathIfExists(mediaId)
                    isDownloadNew?.let {
                        Log.d(TAG, "PLAYING: Custom downloaded song")
                        return@Factory dataSpec.withUri(it)
                    }
                }
            }

            val isDownload =
                downloadCache.isCached(mediaId, dataSpec.position, if (dataSpec.length >= 0) dataSpec.length else 1)
            val isCache = playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)
            if (isDownload || isCache) {
                Log.d(TAG, "PLAYING: remote song (cache = ${isCache}, download = ${isDownload})")
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                return@Factory dataSpec
            }

            songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                Log.d(TAG, "PLAYING: remote song (temp cache), position=${dataSpec.position}, urlExpiry=${(it.second - System.currentTimeMillis()) / 1000}s remaining")
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                val extraHeaders = mutableMapOf("User-Agent" to it.third)
                YouTube.visitorData?.let { visitorData ->
                    extraHeaders["X-Goog-Visitor-Id"] = visitorData
                }
                if (dataSpec.position == 0L && dataSpec.length == androidx.media3.common.C.LENGTH_UNSET.toLong()) {
                    extraHeaders["Range"] = "bytes=0-"
                }
                return@Factory dataSpec.withUri(it.first.toUri())
                    .withAdditionalHeaders(extraHeaders)
            }

            Log.d(TAG, "PLAYING: remote song (online fetch using Resolver)")

            val playbackSource = runCatching {
                runBlocking(Dispatchers.IO) {
                    com.rana.bimo.playback.resolver.NativeInnertubeResolver().resolve(
                        mediaId,
                        audioQuality = audioQuality,
                        connectivityManager = connectivityManager
                    )
                }
            }.getOrElse { throwable ->
                when (throwable) {
                    is PlaybackException -> throw throwable

                    is ConnectException, is UnknownHostException -> {
                        throw PlaybackException(
                            getString(R.string.error_no_internet),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                        )
                    }

                    is SocketTimeoutException -> {
                        throw PlaybackException(
                            getString(R.string.error_timeout),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                        )
                    }

                    else -> throw PlaybackException(
                        getString(R.string.error_unknown),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR
                    )
                }
            }
            
            val playbackData = playbackSource.originalPlaybackData
            if (playbackData != null) {
                val format = playbackData.format
                database.query {
                    upsert(
                        FormatEntity(
                            id = mediaId,
                            itag = format.itag,
                            mimeType = format.mimeType.split(";")[0],
                            codecs = format.mimeType.split("codecs=")[1].removeSurrounding("\""),
                            bitrate = format.bitrate,
                            sampleRate = format.audioSampleRate,
                            contentLength = format.contentLength!!,
                            loudnessDb = playbackData.audioConfig?.loudnessDb,
                            playbackTrackingUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                        )
                    )
                }
                scope.launch(Dispatchers.IO) { recoverSong(mediaId, playbackData) }
            } else {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
            }

            val streamUrl = playbackSource.url

            songUrlCache[mediaId] = Triple(
                streamUrl,
                playbackSource.expiresAt,
                playbackSource.headers["User-Agent"] ?: "none",
            )
            
            return@Factory playbackSource.toDataSpec(dataSpec)
        }
    }

    private fun createRenderersFactory() = object : DefaultRenderersFactory(this) {
        override fun buildAudioSink(
            context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean
        ): AudioSink {
            return DefaultAudioSink.Builder(this@MusicService)
                .setEnableFloatOutput(enableFloatOutput)

                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        emptyArray(),
                        SilenceSkippingAudioProcessor(),
                        SonicAudioProcessor()
                    )
                )
                .setAudioOffloadSupportProvider(DefaultAudioOffloadSupportProvider(context))
                .build()
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        val q = queueBoard.getCurrentQueue()!!
        player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(q.getSize()))
        if (q.shuffled == shuffleModeEnabled) return
        triggerShuffle()
    }

    /**
     * Shuffles the queue
     */
    fun triggerShuffle() {
        val oldIndex = player.currentMediaItemIndex
        queueBoard.setCurrQueuePosIndex(oldIndex)
        val currentQueue = queueBoard.getCurrentQueue() ?: return

        // shuffle and update player playlist
        if (!currentQueue.shuffled) {
            queueBoard.shuffleCurrent()
        } else {
            queueBoard.unShuffleCurrent()
        }
        queueBoard.setCurrQueue()

        updateNotification()
    }

    override fun onPlaybackStatsReady(eventTime: AnalyticsListener.EventTime, playbackStats: PlaybackStats) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        var minPlaybackDur = (dataStore.get(minPlaybackDurKey, 30).toFloat() / 100)
        // ensure within bounds
        if (minPlaybackDur >= 1f) {
            minPlaybackDur = 0.99f // Ehhh 99 is good enough to avoid any rounding errors
        } else if (minPlaybackDur < 0.01f) {
            minPlaybackDur = 0.01f // Still want "spam skipping" to not count as plays
        }

        val playRatio = playbackStats.totalPlayTimeMs.toFloat() / ((mediaItem.metadata?.duration?.times(1000)) ?: -1)
        Log.d(TAG, "Playback ratio: $playRatio Min threshold: $minPlaybackDur")
        if (playRatio >= minPlaybackDur && !dataStore.get(PauseListenHistoryKey, false)) {
            database.query {
                incrementPlayCount(mediaItem.mediaId)
                incrementTotalPlayTime(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = playbackStats.totalPlayTimeMs
                        )
                    )
                } catch (_: SQLException) {
                }
            }

            // TODO: support playlist id
            if (mediaItem.metadata?.isLocal != true && !dataStore.get(PauseRemoteListenHistoryKey, false)) {
                CoroutineScope(Dispatchers.IO).launch {
                    val playbackUrl = database.format(mediaItem.mediaId).first()?.playbackTrackingUrl
                        ?: YTPlayerUtils.playerResponseForMetadata(mediaItem.mediaId, null)
                            .getOrNull()?.playbackTracking?.videostatsPlaybackUrl?.baseUrl

                    playbackUrl?.let {
                        YouTube.registerPlayback(null, playbackUrl)
                            .onFailure {
                                reportException(it)
                            }
                    }
                }
            }
        }
    }

    fun saveQueueToDisk() {
        val data = queueBoard.getAllQueues()
        CoroutineScope(Dispatchers.IO).launch {
            // db on main thread crash, use Dispatchers.IO
            database.rewriteAllQueues(data)
        }

        val pos = player.currentPosition

        runBlocking {
            // async issues, run blocking
            dataStore.edit { settings ->
                settings[LastPosKey] = pos
            }
        }
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // FG keep alive
        if (!(!player.isPlaying && dataStore.get(KeepAliveKey, false))) {
            super.onUpdateNotification(session, startInForegroundRequired)
        }
    }


    override fun onDestroy() {
        Log.i(TAG, "Terminating MusicService.")
        deInitQueue()

        mediaSession.player.stop()
        mediaSession.release()
        mediaSession.player.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    companion object {
        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val SEARCH = "search"

        const val CHANNEL_ID = "music_channel_01"
        const val CHANNEL_NAME = "fgs_workaround"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = 512 * 1024L
    }
}
