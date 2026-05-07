package com.ioristudios.music.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.ioristudios.music.MainActivity
import com.ioristudios.music.R
import com.ioristudios.music.data.model.Song
import com.ioristudios.music.data.playback.PlaybackMode
import com.ioristudios.music.data.playback.PlaybackStatus
import com.ioristudios.music.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class PlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {
    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: MusicRepository
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSession: MediaSession
    private var focusRequest: AudioFocusRequest? = null
    private var player: MediaPlayer? = null
    private var progressJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var shouldResumeOnFocusGain = false
    private var noisyReceiverRegistered = false

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pausePlayback()
        }
    }

    inner class LocalBinder : Binder() {
        fun service(): PlaybackService = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        repository = MusicRepository.getInstance(this)
        audioManager = getSystemService(AudioManager::class.java)
        mediaSession = MediaSession(this, "MusicPlaybackSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resumePlayback()
                override fun onPause() = pausePlayback()
                override fun onSkipToNext() = skipNext()
                override fun onSkipToPrevious() = skipPrevious()
                override fun onSeekTo(pos: Long) = seekTo((pos / 1000L).toLong())
                override fun onStop() = stopPlayback(stopService = true)
            })
            isActive = true
        }
        createNotificationChannel()
        restoreState()

        scope.launch {
            repository.songs.collect { songs ->
                val state = _state.value
                if (state.queue.isEmpty()) return@collect
                
                val byId = songs.associateBy { it.id }
                val newQueue = state.queue.map { song -> byId[song.id] ?: song }
                
                if (newQueue != state.queue) {
                    updateState(queue = newQueue)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_SONG -> playSongById(intent.getLongExtra(EXTRA_SONG_ID, -1L))
            ACTION_PLAY_QUEUE -> playQueue(intent.getLongArrayExtra(EXTRA_QUEUE_IDS)?.toList().orEmpty(), intent.getLongExtra(EXTRA_SONG_ID, -1L))
            ACTION_PLAY_URI -> playExternalUri(intent.data)
            ACTION_PLAY -> resumePlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_TOGGLE -> if (_state.value.isPlaying) pausePlayback() else resumePlayback()
            ACTION_NEXT -> skipNext()
            ACTION_PREVIOUS -> skipPrevious()
            ACTION_SEEK -> seekTo(intent.getLongExtra(EXTRA_POSITION, 0L))
            ACTION_STOP -> stopPlayback(stopService = true)
            ACTION_SET_MODE -> setMode(PlaybackMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: PlaybackMode.NORMAL.name))
            ACTION_SET_VOLUME -> setVolumeBoost(intent.getFloatExtra(EXTRA_VOLUME, 100f))
            else -> refreshNotification()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!_state.value.isPlaying) refreshNotification() else startForeground(NOTIFICATION_ID, buildNotification())
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        persistState()
        progressJob?.cancel()
        unregisterNoisyReceiver()
        releaseWakeLock()
        mediaSession.release()
        player?.release()
        player = null
        abandonAudioFocus()
        super.onDestroy()
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                shouldResumeOnFocusGain = _state.value.isPlaying
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                shouldResumeOnFocusGain = false
                stopPlayback(stopService = false)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (shouldResumeOnFocusGain) resumePlayback()
                shouldResumeOnFocusGain = false
            }
        }
    }

    private fun playSongById(songId: Long) {
        val songs = repository.songs.value
        if (songs.isEmpty()) {
            repository.scanDevice()
            updateState(status = PlaybackStatus.ERROR, error = "No playable songs found")
            return
        }
        val index = songs.indexOfFirst { it.id == songId }.takeIf { it >= 0 } ?: 0
        setQueueAndPlay(songs, index)
    }

    private fun playQueue(queueIds: List<Long>, songId: Long) {
        val byId = repository.songs.value.associateBy { it.id }
        val queue = queueIds.mapNotNull { byId[it] }.ifEmpty { repository.songs.value }
        val index = queue.indexOfFirst { it.id == songId }.takeIf { it >= 0 } ?: 0
        setQueueAndPlay(queue, index)
    }

    private fun playExternalUri(uri: Uri?) {
        if (uri == null) return
        val song = Song(
            id = uri.toString().hashCode().toLong(),
            title = uri.lastPathSegment?.substringAfterLast('/') ?: "External audio",
            artist = "External source",
            duration = 0L,
            contentUri = uri.toString()
        )
        setQueueAndPlay(listOf(song), 0)
    }

    private fun setQueueAndPlay(queue: List<Song>, index: Int, startPositionSeconds: Long = 0L) {
        if (queue.isEmpty()) return
        updateState(queue = queue, queueIndex = index.coerceIn(queue.indices), position = startPositionSeconds, status = PlaybackStatus.LOADING)
        prepareCurrent(startPositionSeconds, autoPlay = true)
    }

    private fun prepareCurrent(startPositionSeconds: Long = 0L, autoPlay: Boolean) {
        val song = _state.value.currentSong ?: return
        val uri = song.contentUri.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return skipNext()
        progressJob?.cancel()
        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setDataSource(applicationContext, uri)
            setOnPreparedListener {
                updateState(
                    duration = (duration / 1000L).takeIf { it > 0 } ?: song.duration,
                    audioSessionId = audioSessionId,
                    status = if (autoPlay) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED,
                    error = null
                )
                if (startPositionSeconds > 0) seekTo((startPositionSeconds * 1000L).toInt())
                applyVolumeBoost()
                if (autoPlay && requestAudioFocus()) {
                    start()
                    repository.recordPlayed(song.id)
                    acquireWakeLock()
                    registerNoisyReceiver()
                    startProgressUpdates()
                }
                refreshNotification()
            }
            setOnCompletionListener { handleCompletion() }
            setOnErrorListener { _, _, _ ->
                updateState(status = PlaybackStatus.ERROR, error = "Unable to play ${song.title}")
                skipNext()
                true
            }
            prepareAsync()
        }
        refreshNotification()
    }

    private fun resumePlayback() {
        val existing = player
        if (existing == null) {
            if (_state.value.currentSong != null) prepareCurrent(_state.value.positionSeconds, autoPlay = true)
            return
        }
        if (requestAudioFocus()) {
            existing.start()
            acquireWakeLock()
            registerNoisyReceiver()
            updateState(status = PlaybackStatus.PLAYING, error = null)
            startProgressUpdates()
            refreshNotification()
        }
    }

    private fun pausePlayback() {
        player?.takeIf { it.isPlaying }?.pause()
        releaseWakeLock()
        updateState(status = PlaybackStatus.PAUSED)
        persistState()
        refreshNotification()
    }

    private fun stopPlayback(stopService: Boolean) {
        persistState()
        progressJob?.cancel()
        player?.stopSafely()
        player?.release()
        player = null
        releaseWakeLock()
        abandonAudioFocus()
        unregisterNoisyReceiver()
        updateState(status = PlaybackStatus.IDLE, position = 0L)
        if (stopService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            refreshNotification()
        }
    }

    private fun seekTo(positionSeconds: Long) {
        val bounded = positionSeconds.coerceAtLeast(0L)
        player?.seekTo((bounded * 1000L).toInt())
        updateState(position = bounded)
        refreshNotification()
    }

    private fun skipNext() {
        val state = _state.value
        if (state.queue.isEmpty()) return
        val nextIndex = when (state.mode) {
            PlaybackMode.SHUFFLE -> Random.nextInt(state.queue.size)
            PlaybackMode.REPEAT -> state.queueIndex
            PlaybackMode.NORMAL -> if (state.queueIndex + 1 < state.queue.size) state.queueIndex + 1 else 0
        }
        updateState(queueIndex = nextIndex, position = 0L, status = PlaybackStatus.LOADING)
        prepareCurrent(autoPlay = true)
    }

    private fun skipPrevious() {
        val state = _state.value
        if (state.queue.isEmpty()) return
        val previousIndex = if (state.queueIndex - 1 >= 0) state.queueIndex - 1 else state.queue.lastIndex
        updateState(queueIndex = previousIndex, position = 0L, status = PlaybackStatus.LOADING)
        prepareCurrent(autoPlay = true)
    }

    private fun setMode(mode: PlaybackMode) {
        updateState(mode = mode)
        persistState()
    }

    private fun setVolumeBoost(volumePercent: Float) {
        updateState(volume = volumePercent.coerceIn(0f, 200f))
        applyVolumeBoost()
        persistState()
    }

    private fun applyVolumeBoost() {
        val gain = (_state.value.volumePercent / 100f).coerceIn(0f, 2f)
        player?.setVolume(gain, gain)
    }

    private fun handleCompletion() {
        if (_state.value.queue.isEmpty()) {
            stopPlayback(stopService = false)
        } else {
            skipNext()
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                player?.let {
                    updateState(
                        position = (it.currentPosition / 1000L).coerceAtLeast(0L),
                        duration = (it.duration / 1000L).coerceAtLeast(_state.value.durationSeconds)
                    )
                    updateMediaSession()
                }
                delay(500L)
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(this)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
        focusRequest = null
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:playback").apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        runCatching { unregisterReceiver(becomingNoisyReceiver) }
        noisyReceiverRegistered = false
    }

    private fun updateState(
        queue: List<Song> = _state.value.queue,
        queueIndex: Int = _state.value.queueIndex,
        position: Long = _state.value.positionSeconds,
        duration: Long = _state.value.durationSeconds,
        status: PlaybackStatus = _state.value.status,
        mode: PlaybackMode = _state.value.mode,
        volume: Float = _state.value.volumePercent,
        audioSessionId: Int = _state.value.audioSessionId,
        error: String? = _state.value.errorMessage
    ) {
        val boundedIndex = if (queue.isEmpty()) -1 else queueIndex.coerceIn(queue.indices)
        _state.value = com.ioristudios.music.data.playback.PlaybackState(
            currentSong = queue.getOrNull(boundedIndex),
            queue = queue,
            queueIndex = boundedIndex,
            positionSeconds = position,
            durationSeconds = duration.takeIf { it > 0 } ?: queue.getOrNull(boundedIndex)?.duration ?: 0L,
            status = status,
            mode = mode,
            volumePercent = volume,
            audioSessionId = audioSessionId,
            errorMessage = error
        )
        updateMediaSession()
    }

    private fun updateMediaSession() {
        val state = _state.value
        val playbackState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_STOP
            )
            .setState(
                when (state.status) {
                    PlaybackStatus.PLAYING -> PlaybackState.STATE_PLAYING
                    PlaybackStatus.PAUSED -> PlaybackState.STATE_PAUSED
                    PlaybackStatus.LOADING -> PlaybackState.STATE_BUFFERING
                    PlaybackStatus.ERROR -> PlaybackState.STATE_ERROR
                    PlaybackStatus.IDLE -> PlaybackState.STATE_STOPPED
                },
                state.positionSeconds * 1000L,
                if (state.isPlaying) 1f else 0f
            )
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun refreshNotification() {
        val notification = buildNotification()
        if (_state.value.status != PlaybackStatus.IDLE) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val state = _state.value
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val playPauseAction = if (state.isPlaying) action(R.drawable.ic_media_pause, "Pause", ACTION_PAUSE, 2)
        else action(R.drawable.ic_media_play, "Play", ACTION_PLAY, 3)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(state.currentSong?.title ?: getString(R.string.app_name))
            .setContentText(state.currentSong?.artist ?: "Ready")
            .setContentIntent(contentIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(state.isPlaying)
            .setProgress(
                state.durationSeconds.toInt().coerceAtLeast(0),
                state.positionSeconds.toInt().coerceAtLeast(0),
                state.status == PlaybackStatus.LOADING
            )
            .addAction(action(R.drawable.ic_media_previous, "Previous", ACTION_PREVIOUS, 4))
            .addAction(playPauseAction)
            .addAction(action(R.drawable.ic_media_next, "Next", ACTION_NEXT, 5))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun action(iconRes: Int, title: String, action: String, requestCode: Int): Notification.Action =
        Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, iconRes),
            title,
            serviceIntent(action, requestCode)
        ).build()

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Music playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Playback controls for active music"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun persistState() {
        val state = _state.value
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_QUEUE, state.queue.joinToString(",") { it.id.toString() })
            .putInt(KEY_INDEX, state.queueIndex)
            .putLong(KEY_POSITION, state.positionSeconds)
            .putString(KEY_MODE, state.mode.name)
            .putFloat(KEY_VOLUME, state.volumePercent)
            .apply()
    }

    private fun restoreState() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val queueIds = prefs.getString(KEY_QUEUE, "")
            .orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }
        val byId = repository.songs.value.associateBy { it.id }
        val queue = queueIds.mapNotNull { byId[it] }
        val mode = runCatching { PlaybackMode.valueOf(prefs.getString(KEY_MODE, PlaybackMode.NORMAL.name)!!) }
            .getOrDefault(PlaybackMode.NORMAL)
        val volume = prefs.getFloat(KEY_VOLUME, 100f)
        if (queue.isNotEmpty()) {
            updateState(
                queue = queue,
                queueIndex = prefs.getInt(KEY_INDEX, 0),
                position = prefs.getLong(KEY_POSITION, 0L),
                mode = mode,
                volume = volume,
                status = PlaybackStatus.PAUSED
            )
        } else {
            updateState(mode = mode, volume = volume)
        }
    }

    private fun MediaPlayer.stopSafely() {
        runCatching { stop() }
    }

    companion object {
        private const val CHANNEL_ID = "music_playback"
        private const val NOTIFICATION_ID = 42
        private const val PREFS = "playback_state"
        private const val KEY_QUEUE = "queue"
        private const val KEY_INDEX = "index"
        private const val KEY_POSITION = "position"
        private const val KEY_MODE = "mode"
        private const val KEY_VOLUME = "volume"

        private const val ACTION_PLAY_SONG = "com.ioristudios.music.action.PLAY_SONG"
        private const val ACTION_PLAY_QUEUE = "com.ioristudios.music.action.PLAY_QUEUE"
        private const val ACTION_PLAY_URI = "com.ioristudios.music.action.PLAY_URI"
        private const val ACTION_PLAY = "com.ioristudios.music.action.PLAY"
        private const val ACTION_PAUSE = "com.ioristudios.music.action.PAUSE"
        private const val ACTION_TOGGLE = "com.ioristudios.music.action.TOGGLE"
        private const val ACTION_NEXT = "com.ioristudios.music.action.NEXT"
        private const val ACTION_PREVIOUS = "com.ioristudios.music.action.PREVIOUS"
        private const val ACTION_SEEK = "com.ioristudios.music.action.SEEK"
        private const val ACTION_STOP = "com.ioristudios.music.action.STOP"
        private const val ACTION_SET_MODE = "com.ioristudios.music.action.SET_MODE"
        private const val ACTION_SET_VOLUME = "com.ioristudios.music.action.SET_VOLUME"

        private const val EXTRA_SONG_ID = "song_id"
        private const val EXTRA_QUEUE_IDS = "queue_ids"
        private const val EXTRA_POSITION = "position"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_VOLUME = "volume"

        private val _state = MutableStateFlow(com.ioristudios.music.data.playback.PlaybackState())
        val state: StateFlow<com.ioristudios.music.data.playback.PlaybackState> = _state.asStateFlow()

        fun playSong(context: Context, song: Song) {
            start(context, Intent(context, PlaybackService::class.java).setAction(ACTION_PLAY_SONG).putExtra(EXTRA_SONG_ID, song.id))
        }

        fun playQueue(context: Context, queue: List<Song>, song: Song) {
            start(
                context,
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_PLAY_QUEUE)
                    .putExtra(EXTRA_QUEUE_IDS, queue.map { it.id }.toLongArray())
                    .putExtra(EXTRA_SONG_ID, song.id)
            )
        }

        fun playUri(context: Context, uri: Uri) {
            start(context, Intent(context, PlaybackService::class.java).setAction(ACTION_PLAY_URI).setData(uri))
        }

        fun toggle(context: Context) = start(context, Intent(context, PlaybackService::class.java).setAction(ACTION_TOGGLE))
        fun pause(context: Context) = start(context, Intent(context, PlaybackService::class.java).setAction(ACTION_PAUSE))
        fun play(context: Context) = start(context, Intent(context, PlaybackService::class.java).setAction(ACTION_PLAY))
        fun next(context: Context) = start(context, Intent(context, PlaybackService::class.java).setAction(ACTION_NEXT))
        fun previous(context: Context) = start(context, Intent(context, PlaybackService::class.java).setAction(ACTION_PREVIOUS))
        fun seek(context: Context, positionSeconds: Long) =
            start(context, Intent(context, PlaybackService::class.java).setAction(ACTION_SEEK).putExtra(EXTRA_POSITION, positionSeconds))
        fun setMode(context: Context, mode: PlaybackMode) =
            start(context, Intent(context, PlaybackService::class.java).setAction(ACTION_SET_MODE).putExtra(EXTRA_MODE, mode.name))
        fun setVolume(context: Context, volumePercent: Float) =
            start(context, Intent(context, PlaybackService::class.java).setAction(ACTION_SET_VOLUME).putExtra(EXTRA_VOLUME, volumePercent))

        private fun start(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun batteryOptimizationIntent(context: Context): Intent =
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
}
