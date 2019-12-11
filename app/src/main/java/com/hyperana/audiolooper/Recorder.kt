package com.hyperana.audiolooper

import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.util.concurrent.Executor

class Recorder(val audioDirectory: File, val dataDirectory: File, val sampleRate: Int) :
    MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener, AudioManager.AudioRecordingCallback()
{
    val TAG = "Recorder"

    val FILE_EXTENSION = ".3gp"

    var filename: String? = null
    var recorder: MediaRecorder? = null
    var recordStartTime: Long? = null
    var recordLeadTime: Long? = null


    override fun onInfo(p0: MediaRecorder?, p1: Int, p2: Int) {
        Log.d(TAG, "on recorder info: $p1, $p2")

    }

    override fun onError(p0: MediaRecorder?, p1: Int, p2: Int) {
        Log.w(TAG, "on recorder error: $p1, $p2")
    }

    fun markStart() {
        recordLeadTime = recordStartTime?.let { System.currentTimeMillis() - it} ?: 0L
    }



    // new mediarecorder made for each go:
    fun prepareFile(fileId: String? = null) {


        // start recording immediately to catch first beat
        recordStartTime = null
        recordLeadTime = null
        filename = fileId ?: "temp"

        File(audioDirectory, filename!! + FILE_EXTENSION).also { file ->
            Log.d(TAG, "outputFile: $file")

            // new mediarecorder made for each go:
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setOutputFile(file.absolutePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
                setAudioChannels(2)
                setAudioSamplingRate(sampleRate)
                setOnInfoListener(this@Recorder)
                setMaxDuration(30 * 1000)
                setOnErrorListener(this@Recorder)

                // todo: use this to start metronome, where possible (record lead time -> 0)?
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                   // registerAudioRecordingCallback(ThreadPerTaskExecutor(), this@Recorder)
                }


                prepare()


            }


        }
    }

    fun begin() {
        Log.d(TAG, "recorder starting: $recorder")

            recorder?.start() //30ms
            recordStartTime = System.currentTimeMillis()
            Log.d(TAG, "record start time")


    }




    fun stop() : Map<String, String> {
        recorder?.stop()
        release()
        return mapOf(
            DATA_RECORD_LEAD to recordLeadTime.toString()
        )

    }


    override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
        super.onRecordingConfigChanged(configs)
        Log.d(TAG, "recording changed: $configs")
    }

    fun release() {
        recorder?.reset()
        recorder?.release()
        recorder = null
    }
}
