/*
 * Copyright (c) 2021 Auxio Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package io.musicplayer.playback.system

// import com.google.android.exoplayer2.ext.flac.LibflacAudioRenderer
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.IBinder
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.RenderersFactory
import com.google.android.exoplayer2.Tracks
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.audio.AudioCapabilities
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import io.musicplayer.BuildConfig
import io.musicplayer.R
import io.musicplayer.music.MusicStore
import io.musicplayer.music.Song
import io.musicplayer.playback.replaygain.ReplayGainAudioProcessor
import io.musicplayer.playback.state.InternalPlayer
import io.musicplayer.playback.state.PlaybackStateDatabase
import io.musicplayer.playback.state.PlaybackStateManager
import io.musicplayer.playback.state.RepeatMode
import io.musicplayer.settings.Settings
import io.musicplayer.ui.system.ForegroundManager
import io.musicplayer.util.logD
import io.musicplayer.widgets.WidgetComponent
import io.musicplayer.widgets.WidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A service that manages the system-side aspects of playback, such as:
 * - The single [ExoPlayer] instance.
 * - The Media Notification
 * - Headset management
 * - Widgets
 *
 * This service is headless and does not manage the playback state. Moreover, the player instance is
 * not the source of truth for the state, but rather the means to control system-side playback. Both
 * of those tasks are what [PlaybackStateManager] is for.
 *
 * TODO: Android Auto
 *
 * @author OxygenCobalt
 */
class PlaybackService :
    Service(),
    Player.Listener,
    InternalPlayer,
    MediaSessionComponent.Callback,
    Settings.Callback,
    MusicStore.Callback {
    // Player components
    private lateinit var player: ExoPlayer
    private lateinit var replayGainProcessor: ReplayGainAudioProcessor

    // System backend components
    private lateinit var mediaSessionComponent: MediaSessionComponent
    private lateinit var widgetComponent: WidgetComponent
    private val systemReceiver = PlaybackReceiver()

    // Managers
    private val playbackManager = PlaybackStateManager.getInstance()
    private val musicStore = MusicStore.getInstance()
    private lateinit var settings: Settings

    // State
    private lateinit var foregroundManager: ForegroundManager
    private var hasPlayed = false
    private var openAudioEffectSession = false

    // Coroutines
    private val serviceJob = Job()
    private val positionScope = CoroutineScope(serviceJob + Dispatchers.Main)
    private val restoreScope = CoroutineScope(serviceJob + Dispatchers.Main)
    private val saveScope = CoroutineScope(serviceJob + Dispatchers.Main)

    // --- SERVICE OVERRIDES ---

    override fun onCreate() {
        super.onCreate()

        replayGainProcessor = ReplayGainAudioProcessor(this)

        // Since Auxio is a music player, only specify an audio renderer to save
        // battery/apk size/cache size
        val audioRenderer = RenderersFactory { handler, _, audioListener, _, _ ->
            arrayOf(
                MediaCodecAudioRenderer(
                    this,
                    MediaCodecSelector.DEFAULT,
                    handler,
                    audioListener,
                    AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES,
                    replayGainProcessor),
                /*LibflacAudioRenderer(handler, audioListener, replayGainProcessor)*/ )
        }

        // Enable constant bitrate seeking so that certain MP3s/AACs are seekable
        val extractorsFactory = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)

        player =
            ExoPlayer.Builder(this, audioRenderer)
                .setMediaSourceFactory(DefaultMediaSourceFactory(this, extractorsFactory))
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true)
                .build()

        player.addListener(this)

        settings = Settings(this, this)
        foregroundManager = ForegroundManager(this)

        playbackManager.registerInternalPlayer(this)
        musicStore.addCallback(this)
        positionScope.launch {
            while (true) {
                playbackManager.synchronizePosition(this@PlaybackService, player.currentPosition)
                delay(POS_POLL_INTERVAL)
            }
        }

        widgetComponent = WidgetComponent(this)
        mediaSessionComponent = MediaSessionComponent(this, player, this)

        IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(AudioManager.ACTION_HEADSET_PLUG)

            addAction(ACTION_INC_REPEAT_MODE)
            addAction(ACTION_INVERT_SHUFFLE)
            addAction(ACTION_SKIP_PREV)
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_SKIP_NEXT)
            addAction(ACTION_EXIT)
            addAction(WidgetProvider.ACTION_WIDGET_UPDATE)

            registerReceiver(systemReceiver, this)
        }

        // --- PLAYBACKSTATEMANAGER SETUP ---

        logD("Service created")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == Intent.ACTION_MEDIA_BUTTON) {
            mediaSessionComponent.handleMediaButtonIntent(intent)
        }

        return START_NOT_STICKY
    }

    // No binding, service is headless
    // Communicate using PlaybackStateManager, SettingsManager, or Broadcasts instead.
    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()

        foregroundManager.release()

        // Pause just in case this destruction was unexpected.
        playbackManager.isPlaying = false

        playbackManager.unregisterInternalPlayer(this)
        musicStore.addCallback(this)
        settings.release()
        unregisterReceiver(systemReceiver)
        serviceJob.cancel()

        widgetComponent.release()
        mediaSessionComponent.release()

        player.release()
        if (openAudioEffectSession) {
            // Make sure to close the audio session when we release the player.
            broadcastAudioEffectAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            openAudioEffectSession = false
        }

        logD("Service destroyed")
    }

    // --- PLAYER OVERRIDES ---

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        super.onPlayWhenReadyChanged(playWhenReady, reason)

        if (playWhenReady) {
            hasPlayed = true
        }

        if (playbackManager.isPlaying != playWhenReady) {
            playbackManager.isPlaying = playWhenReady
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED) {
            if (playbackManager.repeatMode == RepeatMode.TRACK) {
                playbackManager.rewind()
                if (settings.pauseOnRepeat) {
                    playbackManager.isPlaying = false
                }
            } else {
                playbackManager.next()
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        // TODO: Replace with no skipping and a notification instead
        // If there's any issue, just go to the next song.
        playbackManager.next()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        playbackManager.synchronizePosition(this, player.currentPosition)
    }

    override fun onTracksChanged(tracks: Tracks) {
        super.onTracksChanged(tracks)

        for (group in tracks.groups) {
            if (group.isSelected) {
                for (i in 0 until group.length) {
                    if (group.isTrackSelected(i)) {
                        replayGainProcessor.applyReplayGain(group.getTrackFormat(i).metadata)
                        break
                    }
                }

                break
            }
        }
    }

    // --- CONTROLLER OVERRIDES ---

    override val audioSessionId: Int
        get() = player.audioSessionId

    override val shouldRewindWithPrev: Boolean
        get() = settings.rewindWithPrev && player.currentPosition > REWIND_THRESHOLD

    override fun loadSong(song: Song?) {
        if (song == null) {
            // Stop the foreground state if there's nothing to play.
            logD("Nothing playing, stopping playback")
            player.stop()
            if (openAudioEffectSession) {
                // Make sure to close the audio session when we stop playback.
                broadcastAudioEffectAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                openAudioEffectSession = false
            }

            stopAndSave()
            return
        }

        logD("Loading ${song.rawName}")
        player.setMediaItem(MediaItem.fromUri(song.uri))
        player.prepare()

        if (!openAudioEffectSession) {
            // Android does not like it if you start an audio effect session without having
            // something within your player buffer. Make sure we only start one when we load
            // a song.
            broadcastAudioEffectAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            openAudioEffectSession = true
        }
    }

    private fun broadcastAudioEffectAction(event: String) {
        sendBroadcast(
            Intent(event)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC))
    }

    /** Stop the foreground state and hide the notification */
    private fun stopAndSave() {
        hasPlayed = false

        if (foregroundManager.tryStopForeground()) {
            logD("Saving playback state")
            saveScope.launch {
                playbackManager.saveState(PlaybackStateDatabase.getInstance(this@PlaybackService))
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        logD("Seeking to ${positionMs}ms")
        player.seekTo(positionMs)
    }

    override fun onPlayingChanged(isPlaying: Boolean) {
        player.playWhenReady = isPlaying
    }

    override fun onAction(action: InternalPlayer.Action): Boolean {
        val library = musicStore.library
        if (library != null) {
            logD("Performing action: $action")

            when (action) {
                is InternalPlayer.Action.RestoreState -> {
                    restoreScope.launch {
                        playbackManager.restoreState(
                            PlaybackStateDatabase.getInstance(this@PlaybackService), false)
                    }
                }
                is InternalPlayer.Action.ShuffleAll -> {
                    playbackManager.shuffleAll(settings)
                }
                is InternalPlayer.Action.Open -> {
                    library.findSongForUri(application, action.uri)?.let { song ->
                        playbackManager.play(song, settings.libPlaybackMode, settings)
                    }
                }
            }

            return true
        }

        return false
    }

    // --- MEDIASESSIONCOMPONENT OVERRIDES ---

    override fun onPostNotification(
        notification: NotificationComponent?,
        reason: MediaSessionComponent.PostingReason
    ) {
        if (notification == null) {
            // This case is only here if I ever need to move foreground stopping from
            // the player code to the notification code.
            logD("No notification, ignoring")
            return
        }

        if (hasPlayed) {
            logD("Updating notification [Reason: $reason]")
            if (!foregroundManager.tryStartForeground(notification)) {
                notification.post()
            }
        }
    }

    // --- MUSICSTORE OVERRIDES ---

    override fun onLibraryChanged(library: MusicStore.Library?) {
        if (library != null) {
            playbackManager.requestAction(this)
        }
    }

    // --- SETTINGSMANAGER OVERRIDES ---

    override fun onSettingChanged(key: String) {
        if (key == getString(R.string.set_key_replay_gain) ||
            key == getString(R.string.set_key_pre_amp_with) ||
            key == getString(R.string.set_key_pre_amp_without)) {
            onTracksChanged(player.currentTracks)
        }
    }

    // --- OTHER FUNCTIONS ---

    /** A [BroadcastReceiver] for receiving general playback events from the system. */
    private inner class PlaybackReceiver : BroadcastReceiver() {
        private var initialHeadsetPlugEventHandled = false

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // --- SYSTEM EVENTS ---

                // Android has three different ways of handling audio plug events for some reason:
                // 1. ACTION_HEADSET_PLUG, which only works with wired headsets
                // 2. ACTION_ACL_CONNECTED, which allows headset autoplay but also requires
                // granting the BLUETOOTH/BLUETOOTH_CONNECT permissions, which is more or less
                // a non-starter since both require me to display a permission prompt
                // 3. Some weird internal framework thing that also handles bluetooth headsets???
                //
                // They should have just stopped at ACTION_HEADSET_PLUG.
                AudioManager.ACTION_HEADSET_PLUG -> {
                    when (intent.getIntExtra("state", -1)) {
                        0 -> pauseFromPlug()
                        1 -> maybeResumeFromPlug()
                    }

                    initialHeadsetPlugEventHandled = true
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> pauseFromPlug()

                // --- AUXIO EVENTS ---
                ACTION_PLAY_PAUSE -> playbackManager.isPlaying = !playbackManager.isPlaying
                ACTION_INC_REPEAT_MODE ->
                    playbackManager.repeatMode = playbackManager.repeatMode.increment()
                ACTION_INVERT_SHUFFLE ->
                    playbackManager.reshuffle(!playbackManager.isShuffled, settings)
                ACTION_SKIP_PREV -> playbackManager.prev()
                ACTION_SKIP_NEXT -> playbackManager.next()
                ACTION_EXIT -> {
                    playbackManager.isPlaying = false
                    stopAndSave()
                }
                WidgetProvider.ACTION_WIDGET_UPDATE -> widgetComponent.update()
            }
        }

        /**
         * Resume from a headset plug event in the case that the quirk is enabled. This
         * functionality remains a quirk for two reasons:
         * 1. Automatically resuming more or less overrides all other audio streams, which is not
         * that friendly
         * 2. There is a bug where playback will always start when this service starts, mostly due
         * to AudioManager.ACTION_HEADSET_PLUG always firing on startup. This is fixed, but I fear
         * that it may not work on OEM skins that for whatever reason don't make this action fire.
         */
        private fun maybeResumeFromPlug() {
            if (playbackManager.song != null &&
                settings.headsetAutoplay &&
                initialHeadsetPlugEventHandled) {
                logD("Device connected, resuming")
                playbackManager.isPlaying = true
            }
        }

        /** Pause from a headset plug. */
        private fun pauseFromPlug() {
            if (playbackManager.song != null) {
                logD("Device disconnected, pausing")
                playbackManager.isPlaying = false
            }
        }
    }

    companion object {
        private const val POS_POLL_INTERVAL = 100L
        private const val REWIND_THRESHOLD = 3000L

        const val ACTION_INC_REPEAT_MODE = BuildConfig.APPLICATION_ID + ".action.LOOP"
        const val ACTION_INVERT_SHUFFLE = BuildConfig.APPLICATION_ID + ".action.SHUFFLE"
        const val ACTION_SKIP_PREV = BuildConfig.APPLICATION_ID + ".action.PREV"
        const val ACTION_PLAY_PAUSE = BuildConfig.APPLICATION_ID + ".action.PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = BuildConfig.APPLICATION_ID + ".action.NEXT"
        const val ACTION_EXIT = BuildConfig.APPLICATION_ID + ".action.EXIT"
    }
}
