package com.hyperana.audiolooper

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.*
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit


class Player(val applicationContext: Context, val audioFile: File, val audioData: Map<String, String>?) {
    val TAG = "Player"
    var seekTo: Long? = null

    var isAlt = false
    var player: MediaPlayer? = null
    var altPlayer: MediaPlayer? = null
    var nextPlayer: MediaPlayer? = null
        get() {
            isAlt = !isAlt
            return if (isAlt) altPlayer else player
        }
    var playTimer: ScheduledThreadPoolExecutor? = null

    var isReady = false

    //todo: make this a prepare step so mediaplayers can be dumped and recreated on error
    init {
        Log.d(TAG, "initialize player: \n${audioFile.absolutePath}\n${audioData}")

        seekTo = audioData?.get(DATA_RECORD_LEAD)?.toLongOrNull()
        preparePlayers()
    }

    fun preparePlayers() {
        isReady = false
        player?.release()
        player = null
        altPlayer?.release()
        altPlayer = null

        altPlayer = createMediaPlayer(applicationContext).apply {

            // prepare, but don't start:
            setOnPreparedListener { player ->
                Log.d(TAG, "mediaplayer2 prepared")

                player.seekTo(seekTo ?: 0, MediaPlayer.SEEK_CLOSEST_SYNC)
                Log.d(TAG, "mediaplayer2  @seekTo: $seekTo")
            }
            // on completion, seek again to be ready for next play
            setOnCompletionListener { thisPlayer ->
                Log.d(TAG, "player complete")
                thisPlayer.seekTo(seekTo ?: 0, MediaPlayer.SEEK_CLOSEST_SYNC)
                Log.d(TAG, "mediaplayer2  @seekTo: $seekTo")
            }

        }


        player = createMediaPlayer(applicationContext).apply {
            setOnBufferingUpdateListener { mediaPlayer, i ->  Log.d(TAG, "buffering update: $i")}
            setOnTimedTextListener { mediaPlayer, timedText ->  Log.d(TAG, "timedText: $timedText")}

            // when ready, seek, start playback, and set delayed start for metronome:
            setOnPreparedListener { player ->
                Log.d(TAG, "mediaplayer1 prepared")

                player.seekTo(seekTo ?: 0, MediaPlayer.SEEK_CLOSEST_SYNC)
                isReady = true
                Log.d(TAG, "mediaplayer1  @seekTo: $seekTo")

            }
            // on completion, seek again to be ready for next play
            setOnCompletionListener { thisPlayer ->
                Log.d(TAG, "player complete")
                thisPlayer.seekTo(seekTo ?: 0, MediaPlayer.SEEK_CLOSEST_SYNC)
                Log.d(TAG, "mediaplayer1  @seekTo: $seekTo")
            }

        }
    }

    fun startRepeatedPlayback(delay: Long, period: Long) {

        playTimer?.shutdownNow()

        playTimer = ScheduledThreadPoolExecutor(4).apply {

            // schedule repeated alternating plays, skipping if in an unprepared state
            scheduleAtFixedRate(
                object : TimerTask() {

                    override fun run() {
                        if (isReady) {
                            nextPlayer?.start()
                            Log.d(TAG, "mediaplayer started")
                        }
                        else {
                            Log.w(TAG, "mediaplayer not ready at start")
                        }
                    }
                },
                delay,
                period,
               TimeUnit.MILLISECONDS
            )
        }
    }

    fun stop() {
        Log.d(TAG, "stop")

        playTimer?.shutdownNow()
        playTimer = null

       preparePlayers()
    }

    fun createMediaPlayer(applicationContext: Context) : MediaPlayer {
        return MediaPlayer.create(applicationContext, Uri.fromFile(audioFile)).apply {
            setOnErrorListener { mediaPlayer, i, i2 ->
                Log.w(TAG, "mediaplayer error $i, $i2")

                // dump errored players and recreate:
                preparePlayers()

                true // oncompletionhandler will not be called
            }
            setOnInfoListener { mediaPlayer, i, i2 ->
                Log.d(TAG, "mediaplayer info $i, $i2")
                true
            }

        }
    }


    // after release, the object is defunct and cannot be restarted!
    fun release() {
        isReady = false
        playTimer?.shutdownNow()
        playTimer = null


        player?.reset()
        player?.release()
        player = null

        altPlayer?.reset()
        altPlayer?.release()
        altPlayer = null

    }
}
