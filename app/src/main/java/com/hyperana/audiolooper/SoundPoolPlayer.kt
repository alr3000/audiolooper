package com.hyperana.audiolooper

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioManager.STREAM_MUSIC
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.*
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

// todo: listener alerts for each sound when ready/errored, started, complete
class SoundPoolPlayer(val applicationContext: Context, val audioFile: File, val audioData: Map<String, String>?) {
    val TAG = "Player"
    val VOLUME = 0.5f

    var soundId: Int? = null
    var streamIds: MutableSet<Int> = mutableSetOf()
    var silentStream: Int? = null

    var player = SoundPool.Builder()
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build())
        .build().apply {
        setOnLoadCompleteListener { soundPool, i, i2 ->
            Log.d(TAG, "loadComplete: $i, $i2, starting silent loop...")

            // get it ready to trim 20 extra ms from first play?:
      //      silentStream = soundPool.play(i, .01f, .01f, 1, 1, 1f)
            isReady = true

        }

    }
    var playTimer: ScheduledThreadPoolExecutor? = null

    var isReady = false
    var isPlaying = false

    init {
        Log.d(TAG, "initialize player: \n${audioFile.absolutePath}\n${audioData}")
        preparePlayers()
    }

    fun preparePlayers() {
        isReady = false

        player.apply {
            soundId = load(audioFile.absolutePath, 1)
        }


    }

    fun play(delay: Long = 0) {
        playTimer?.shutdown()
        playTimer = ScheduledThreadPoolExecutor(2).apply {
            schedule({
                Log.d(TAG, "Sound ($soundId) requested")
                if (isReady && isPlaying) {
                    soundId?.also {
                        streamIds.add(player.play(it, VOLUME, VOLUME, 1, 0, 1f))
                    }
                    Log.d(TAG, "Sound ($soundId) started")
                }
                else {
                    Log.w(TAG, "Sound ($soundId) not ready at start")
                }
            }, delay, TimeUnit.MILLISECONDS)
        }
        isPlaying = true
        Log.d(TAG, "scheduled playback in $delay ms")
    }

    fun startRepeatedPlayback(delay: Long, period: Long) {
        Log.d(TAG, "startRepeatedPlayback: Sound ($soundId) scheduled: $delay/$period")

        soundId?.also { silentStream = player.play(it, .001f, 0f, 1, 1, 1f)}
            ?: Log.w(TAG, "sound not ready")

        playTimer?.shutdown()

        playTimer = ScheduledThreadPoolExecutor(2).apply {

            // schedule repeated alternating plays, skipping if in an unprepared state
            scheduleAtFixedRate(
                object : TimerTask() {

                    override fun run() {
                        val time = System.nanoTime()
                        if (isReady && isPlaying) {
                            soundId?.also {
                                streamIds.add(player.play(it, VOLUME, VOLUME, 1, 0, 1f))
                            }
                            Log.d(TAG, "Sound ($soundId) started after ${(System.nanoTime() - time)/1000000}ms latency")
                        }
                        else {
                            Log.w(TAG, "Sound ($soundId) not ready at start")
                        }
                    }
                },
                delay,
                period,
                TimeUnit.MILLISECONDS
            )
        }
        isPlaying = true
        Log.d(TAG, "scheduled playback in $delay ms")
    }

    fun stop() {
        Log.d(TAG, "stop")
        isPlaying = false
        playTimer?.shutdownNow()
        playTimer = null

        streamIds.forEach {
            player.stop(it)
        }.also { Log.d(TAG, "stopped ${streamIds.size}")}

        streamIds.clear()

        silentStream?.also { player?.stop(it)}
    }

    fun pause() {
        silentStream?.also { player?.stop(it)}
    }

    // after release, the object is defunct and cannot be restarted!
    fun release() {
        Log.d(TAG, "release")
        isReady = false
        playTimer?.shutdownNow()
        playTimer = null


        player?.release()
        player = null

    }
}
