/*
* Copyright (C) 2014 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package it.cammino.risuscito.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import it.cammino.risuscito.PaginaRenderActivity
import it.cammino.risuscito.R
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
 * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
 * exposes it through its MediaSession.Token, which allows the client to create a MediaController
 * that connects to and send control commands to the MediaSession remotely. This is useful for
 * user interfaces that need to interact with your media session, like Android Auto. You can
 * (should) also use the same service from your app's UI, which gives a seamless playback
 * experience to the user.
 *
 *
 * To implement a MediaBrowserService, you need to:
 *
 *
 *
 *
 *
 *  *  Extend [android.support.v4.media.MediaBrowserServiceCompat], implementing the media
 * browsing related methods [android.support.v4.media.MediaBrowserServiceCompat.onGetRoot] and
 * [android.support.v4.media.MediaBrowserServiceCompat.onLoadChildren];
 *  *  In onCreate, start a new [android.support.v4.media.session.MediaSessionCompat] and
 * notify its parent with the session's token
 * [android.support.v4.media.MediaBrowserServiceCompat.setSessionToken];
 *
 *
 *  *  Set a callback on the
 * [android.support.v4.media.session.MediaSessionCompat.setCallback].
 * The callback will receive all the user's actions, like play, pause, etc;
 *
 *
 *  *  Handle all the actual music playing using any method your app prefers (for example,
 * [android.media.MediaPlayer])
 *
 *
 *  *  Update playbackState, "now playing" metadata and queue, using MediaSession proper methods
 * [android.support.v4.media.session.MediaSessionCompat.setPlaybackState]
 * [android.support.v4.media.session.MediaSessionCompat.setMetadata] and
 * if your implementation allows it,
 * [android.support.v4.media.session.MediaSessionCompat.setQueue])
 *
 *
 *  *  Declare and export the service in AndroidManifest with an intent receiver for the action
 * android.media.browse.MediaBrowserService
 *  *  Declare a broadcast receiver to receive media button events. This is required if your app
 * supports Android KitKat or previous:
 * &lt;receiver android:name="android.support.v4.media.session.MediaButtonReceiver"&gt;
 * &lt;intent-filter&gt;
 * &lt;action android:name="android.intent.action.MEDIA_BUTTON" /&gt;
 * &lt;/intent-filter&gt;
 * &lt;/receiver&gt;
 *
 *
 *
 *
 *
 * To make your app compatible with Android Auto, you also need to:
 *
 *
 *
 *
 *
 *  *  Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
 * with a &lt;automotiveApp&gt; root element. For a media app, this must include
 * an &lt;uses name="media"/&gt; element as a child.
 * For example, in AndroidManifest.xml:
 * &lt;meta-data android:name="com.google.android.gms.car.application"
 * android:resource="@xml/automotive_app_desc"/&gt;
 * And in res/values/automotive_app_desc.xml:
 * &lt;automotiveApp&gt;
 * &lt;uses name="media"/&gt;
 * &lt;/automotiveApp&gt;
 *
 *
 *
 *
 */

class MusicService : MediaBrowserServiceCompat() {

    private var mMusicProvider: MusicProvider? = null
    private var mSession: MediaSessionCompat? = null
    private lateinit var mNotificationManager: NotificationManagerCompat
    // Indicates whether the service was started.
    private var mServiceStarted: Boolean = false
    private var mPlayback: Playback? = null
    private var mCurrentMedia: MediaSessionCompat.QueueItem? = null
    private var mAudioBecomingNoisyReceiver: AudioBecomingNoisyReceiver? = null

    /**
     * Custom [Handler] to process the delayed stop command.
     */
    private val mDelayedStopHandler = Handler(Handler.Callback { msg ->
        if (msg == null || msg.what != STOP_CMD) {
            return@Callback false
        }

        if (!mPlayback!!.isPlaying) {
            Log.d(TAG, "Stopping service")
            stopSelf()
            mServiceStarted = false
        }
        false
    })

    /*
     * (non-Javadoc)
     * @see android.app.Service#onCreate()
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        mMusicProvider = MusicProvider(applicationContext)
        sendMusicProviderStatusBroadcast(false)
        mMusicProvider!!.retrieveMediaAsync(object : MusicProvider.Callback {
            override fun onMusicCatalogReady(success: Boolean) {
                sendMusicProviderStatusBroadcast(success)
            }
        })

        // Start a new MediaSession.
        mSession = MediaSessionCompat(this, TAG)
        sessionToken = mSession!!.sessionToken
        mSession!!.setCallback(MediaSessionCallback())
        mSession!!.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        mPlayback = Playback(this, mMusicProvider)
        mPlayback!!.setCallback(object : Playback.Callback {
            override fun onPlaybackStatusChanged(state: Int) {
                updatePlaybackState(null)
            }

            override fun onCompletion() {
                // In this simple implementation there isn't a play queue, so we simply 'stop' after
                // the song is over.
                handleStopRequest()
            }

            override fun onError(error: String) {
                updatePlaybackState(error)
            }
        })

        val context = applicationContext

        // This is an Intent to launch the app's UI, used primarily by the ongoing notification.
        val intent = Intent(context, PaginaRenderActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_CANCEL_CURRENT)
        mSession!!.setSessionActivity(pi)

        mNotificationManager = NotificationManagerCompat.from(this)
        mAudioBecomingNoisyReceiver = AudioBecomingNoisyReceiver(this)

        updatePlaybackState(null)
    }

    /**
     * (non-Javadoc)
     *
     * @see android.app.Service.onStartCommand
     */
    override fun onStartCommand(startIntent: Intent, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mSession, startIntent)
        return super.onStartCommand(startIntent, flags, startId)
    }

    /**
     * (non-Javadoc)
     *
     * @see android.app.Service.onDestroy
     */
    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        // Service is being killed, so make sure we release our resources
        handleStopRequest()

        mDelayedStopHandler.removeCallbacksAndMessages(null)
        // Always release the MediaSession to clean up resources
        // and notify associated MediaController(s).
        mSession!!.release()
    }

    override fun onGetRoot(clientPackageName: String,
                           clientUid: Int, rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot? {
        // Verify the client is authorized to browse media and return the root that
        // makes the most sense here. In this example we simply verify the package name
        // is the same as ours, but more complicated checks, and responses, are possible
        return if (clientPackageName != packageName) {
            // Allow the client to connect, but not browse, by returning an empty root
            MediaBrowserServiceCompat.BrowserRoot(MusicProvider.MEDIA_ID_EMPTY_ROOT, null)
        } else MediaBrowserServiceCompat.BrowserRoot(MusicProvider.MEDIA_ID_EMPTY_ROOT, null)
        /* Per non far restituire nessuna lista è stata sostituita l'istruzione
            return new BrowserRoot(MEDIA_ID_ROOT, null);
         */
    }

    override fun onLoadChildren(parentMediaId: String,
                                result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {
        Log.d(TAG, "OnLoadChildren: parentMediaId=$parentMediaId")

        if (!mMusicProvider!!.isInitialized) {
            // Use result.detach to allow calling result.sendResult from another thread:
            result.detach()

            mMusicProvider!!.retrieveMediaAsync(object : MusicProvider.Callback {
                override fun onMusicCatalogReady(success: Boolean) {
                    if (success) {
                        loadChildrenImpl(parentMediaId, result)
                    } else {
                        updatePlaybackState(getString(R.string.error_no_metadata))
                        result.sendResult(emptyList())
                    }
                }
            })

        } else {
            // If our music catalog is already loaded/cached, load them into result immediately
            loadChildrenImpl(parentMediaId, result)
        }
    }

    /**
     * Actual implementation of onLoadChildren that assumes that MusicProvider is already
     * initialized.
     */
    private fun loadChildrenImpl(parentMediaId: String,
                                 result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {
        val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()

        when (parentMediaId) {
            MusicProvider.MEDIA_ID_ROOT -> mMusicProvider!!.allMusics.mapTo(mediaItems) {
                MediaBrowserCompat.MediaItem(it.description,
                        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
            }
            MusicProvider.MEDIA_ID_EMPTY_ROOT -> {
            }
            else -> Log.w(TAG, "Skipping unmatched parentMediaId: $parentMediaId")
        }// Since the client provided the empty root we'll just send back an
        // empty list
        result.sendResult(mediaItems)
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            Log.d(TAG, "playFromMediaId mediaId:$mediaId  extras=$extras")

            // The mediaId used here is not the unique musicId. This one comes from the
            // MediaBrowser, and is actually a "hierarchy-aware mediaID": a concatenation of
            // the hierarchy in MediaBrowser and the actual unique musicID. This is necessary
            // so we can build the correct playing queue, based on where the track was
            // selected from.
            val media = mMusicProvider!!.getMusic(mediaId!!)
            if (media != null) {
                mCurrentMedia = MediaSessionCompat.QueueItem(media.description, media.hashCode().toLong())

                // play the music
                handlePlayRequest()
            }
        }

        override fun onPlay() {
            Log.d(TAG, "play")
            if (mCurrentMedia != null) {
                handlePlayRequest()
            }
        }

        override fun onSeekTo(position: Long) {
            Log.d(TAG, "onSeekTo:$position")
            mPlayback!!.seekTo(position.toInt())
        }

        override fun onPause() {
            Log.d(TAG, "pause. current state=" + mPlayback!!.state)
            handlePauseRequest()
        }

        override fun onStop() {
            Log.d(TAG, "stop. current state=" + mPlayback!!.state)
            handleStopRequest()
        }

        override fun onSkipToPrevious() {
            Log.d(TAG, "skip to previous. current state=" + mPlayback!!.state)
            if (mPlayback!!.state == PlaybackStateCompat.STATE_PAUSED)
                handlePlayRequest()
            mPlayback!!.seekTo(0)
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
            val mKeyEvent = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            Log.d(TAG, "onMediaButtonEvent keycode: " + mKeyEvent.keyCode)
            if (mKeyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                onSkipToPrevious()
            return super.onMediaButtonEvent(mediaButtonEvent)
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            Log.d(TAG, "onCustomAction: " + action!!)
            if (ACTION_REFRESH == action) {
                mMusicProvider = MusicProvider(applicationContext)
                mPlayback!!.setmMusicProvider(mMusicProvider!!)
                sendMusicProviderStatusBroadcast(false)
                mMusicProvider!!.retrieveMediaAsync(
                        object : MusicProvider.Callback {
                            override fun onMusicCatalogReady(success: Boolean) {
                                sendMusicProviderStatusBroadcast(success)
                            }
                        })
            }
            super.onCustomAction(action, extras)
        }
    }

    /**
     * Handle a request to play music
     */
    private fun handlePlayRequest() {
        Log.d(TAG, "handlePlayRequest: mState=" + mPlayback!!.state)

        if (mCurrentMedia == null) {
            // Nothing to play
            return
        }

        mDelayedStopHandler.removeCallbacksAndMessages(null)
        if (!mServiceStarted) {
            Log.v(TAG, "Starting service")
            // The MusicService needs to keep running even after the calling MediaBrowser
            // is disconnected. Call startService(Intent) and then stopSelf(..) when we no longer
            // need to play media.
            startService(Intent(applicationContext, MusicService::class.java))
            //            ContextCompat.startForegroundService(getApplicationContext(), new Intent(getApplicationContext(), MusicService.class));
            mServiceStarted = true
        }

        if (!mSession!!.isActive) {
            mSession!!.isActive = true
        }

        updateMetadata()
        mPlayback!!.play(mCurrentMedia!!)
    }

    /**
     * Handle a request to pause music
     */
    private fun handlePauseRequest() {
        Log.d(TAG, "handlePauseRequest: mState=" + mPlayback!!.state)
        mPlayback!!.pause()

        // reset the delayed stop handler.
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mDelayedStopHandler.sendEmptyMessageDelayed(STOP_CMD, STOP_DELAY)
    }

    /**
     * Handle a request to stop music
     */
    private fun handleStopRequest() {
        Log.d(TAG, "handleStopRequest: mState=" + mPlayback!!.state)
        mPlayback!!.stop()
        // reset the delayed stop handler.
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mDelayedStopHandler.sendEmptyMessage(STOP_CMD)

        updatePlaybackState(null)
    }

    private fun updateMetadata() {
        val queueItem = mCurrentMedia
        val musicId = queueItem!!.description.mediaId
        val track = mMusicProvider!!.getMusic(musicId!!)

        mSession!!.setMetadata(track)

    }

    /**
     * Update the current media player state, optionally showing an error message.
     *
     * @param error if not null, error message to present to the user.
     */
    private fun updatePlaybackState(error: String?) {
        Log.d(TAG, "updatePlaybackState, playback state=" + mPlayback!!.state)
        var position = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
        if (mPlayback != null && mPlayback!!.isConnected) {
            position = mPlayback!!.currentStreamPosition.toLong()
        }

        var playbackActions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
        if (mPlayback!!.isPlaying) {
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_PAUSE
        }

        val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(playbackActions)

        var state = mPlayback!!.state

        // If there is an error message, send it to the playback state:
        if (error != null) {
            // Error states are really only supposed to be used for errors that cause playback to
            // stop unexpectedly and persist until the user takes action to fix it.
            stateBuilder.setErrorMessage(-1, error)
            state = PlaybackStateCompat.STATE_ERROR
        }

        // Because the playback state is pulled from the Playback class lint thinks it may not
        // match permitted values.

        stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime())

        // Set the activeQueueItemId if the current index is valid.
        if (mCurrentMedia != null) {
            stateBuilder.setActiveQueueItemId(mCurrentMedia!!.queueId)
        }

        mSession!!.setPlaybackState(stateBuilder.build())

        if (state == PlaybackStateCompat.STATE_PLAYING) {
            val oldMetadataCompat = mMusicProvider!!.getMusic(mCurrentMedia!!.description.mediaId!!)
            val newMetadataCompat = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, oldMetadataCompat!!.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID))
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, oldMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI))
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, oldMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, oldMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mPlayback!!.duration)
                    .putString(MediaMetadataCompat.METADATA_KEY_GENRE, oldMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_GENRE))
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, "")
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, oldMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
                    .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, oldMetadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER))
                    .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, oldMetadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS))
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, oldMetadataCompat.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, oldMetadataCompat.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON))
                    .build()
            mMusicProvider!!.updateMusic(mCurrentMedia!!.description.mediaId!!, newMetadataCompat)
            mSession!!.setMetadata(newMetadataCompat)
            val notification = postNotification()
            startForeground(NOTIFICATION_ID, notification)
            mAudioBecomingNoisyReceiver!!.register()
        } else {
            if (state == PlaybackStateCompat.STATE_PAUSED) {
                postNotification()
            } else {
                mNotificationManager.cancel(NOTIFICATION_ID)
            }
            stopForeground(false)
            mAudioBecomingNoisyReceiver!!.unregister()
        }
    }

    private fun postNotification(): Notification? {
        val notification = MediaNotificationHelper.createNotification(this, mSession!!) ?: return null

        mNotificationManager.notify(NOTIFICATION_ID, notification)
        return notification
    }

    /**
     * Implementation of the AudioManager.ACTION_AUDIO_BECOMING_NOISY Receiver.
     */

    private inner class AudioBecomingNoisyReceiver(context: Context) : BroadcastReceiver() {
        private val mContext: Context = context.applicationContext
        private var mIsRegistered = false

        private val mAudioNoisyIntentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

        fun register() {
            if (!mIsRegistered) {
                mContext.registerReceiver(this, mAudioNoisyIntentFilter)
                mIsRegistered = true
            }
        }

        fun unregister() {
            if (mIsRegistered) {
                mContext.unregisterReceiver(this)
                mIsRegistered = false
            }
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                handlePauseRequest()
            }
        }
    }

    private fun sendMusicProviderStatusBroadcast(done: Boolean) {
        val intentBroadcast = Intent(BROADCAST_RETRIEVE_ASYNC)
        intentBroadcast.putExtra(MSG_RETRIEVE_DONE, done)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intentBroadcast)
    }

    companion object {
        private val TAG = MusicService::class.java.simpleName

        // ID for our MediaNotification.
        const val NOTIFICATION_ID = 412

        // Request code for starting the UI.
        private const val REQUEST_CODE = 99

        // Delay stopSelf by using a handler.
        private val STOP_DELAY = TimeUnit.MINUTES.toMillis(10)
        private const val STOP_CMD = 0x7c48

        const val ACTION_REFRESH = "itcr_media_action_refresh"
        const val BROADCAST_RETRIEVE_ASYNC = "itcr_media_broadcast_retrieve_async"
        const val MSG_RETRIEVE_DONE = "itcr_media_retrieve_done"
    }

}