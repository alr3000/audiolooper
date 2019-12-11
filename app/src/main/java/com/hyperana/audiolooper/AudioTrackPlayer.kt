package com.hyperana.audiolooper

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.loader.content.AsyncTaskLoader
import java.io.File
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.*


class AudioTrackPlayer(val applicationContext: Context, val audioFile: File, val audioData: Map<String, String>?) {
    val TAG = "Player"
    var seekTo: Long? = null

    val BUFFER_SIZE = 1024 * 1024
    val buffer = ByteBuffer.allocate(BUFFER_SIZE)

    var player: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build())
        .setAudioFormat(AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_DEFAULT)
            .setSampleRate(APP_SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build())
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(BUFFER_SIZE)
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .build()


    var playTimer: ScheduledThreadPoolExecutor? = null
    var executor: Executor = Executors.newCachedThreadPool()

    var isReady = false

    //todo: make this a prepare step so mediaplayers can be dumped and recreated on error
    init {
        Log.d(TAG, "initialize player: \n${audioFile.absolutePath}\n${audioData}")

        seekTo = audioData?.get(DATA_RECORD_LEAD)?.toLongOrNull()
        prepareTrack()
    }

    private fun prepareTrack() {
        isReady = false

        executor.execute {
            try {
                Log.d(TAG, "loading file...${audioFile.length()} bytes")
                loadFileToBuffer(audioFile, buffer.array())
                player.write(buffer, BUFFER_SIZE, AudioTrack.MODE_STATIC)
                isReady = true
                Log.d(TAG, "audiotrack ready")
            }
            catch (e: Exception) {
                Log.e(TAG, "failed prepare audioTrack: ${audioFile.absolutePath}")
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
                        if (isReady && (player.setPlaybackHeadPosition(0) == AudioTrack.SUCCESS)) {
                            Log.d(TAG, "player run")
                            player.stop()
                            player.setPlaybackHeadPosition(0)
                            player.play()
                            Log.d(TAG, "player started")
                        }
                        else {
                            Log.w(TAG, "player not ready at start")
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

        player.stop()
        player.setPlaybackHeadPosition(0)
    }

      // after release, the object is defunct and cannot be restarted!
    fun release() {
        isReady = false

        playTimer?.shutdownNow()
        playTimer = null


        player.release()

    }
}
