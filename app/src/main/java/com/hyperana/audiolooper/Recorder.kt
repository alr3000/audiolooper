package com.hyperana.audiolooper

import android.media.MediaRecorder
import android.util.JsonWriter
import android.util.Log
import java.io.File

class Recorder(val audioDirectory: File, val dataDirectory: File) :
    MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener
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
    fun start(fileId: String? = null) {


        // start recording immediately to catch first beat
        recordStartTime = null
        recordLeadTime = null
        filename = fileId ?: "temp"

        File(audioDirectory, filename!! + FILE_EXTENSION).also { file ->
            Log.d(TAG, "outputFile: $file")

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setOutputFile(file.absolutePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC_ELD)
                setOnInfoListener(this@Recorder)
                setMaxDuration(30 * 1000)
                setOnErrorListener(this@Recorder)
                //registerAudioRecordingCallback(null, recordingCallback)


                prepare()
                start()
                recordStartTime = System.currentTimeMillis()

            }
        }

    }

    fun stop() {
        try {
            saveFileData(
                filename!!, mapOf(
                    DATA_RECORD_LEAD to recordLeadTime.toString()
                )
            )
        }
        catch (e: Exception) {
            Log.d(TAG, "failed save data", e)
        }
    }



    fun saveFileData(filename: String, map: Map<String, String>) {

        File(dataDirectory, filename).outputStream().writer()
            .also { writer ->
                JsonWriter(writer).also { json ->
                    json.beginObject()
                    map.entries.forEach { (k, v) ->
                        json.name(k)
                        json.value(v)
                    }
                    json.endObject()
                }
                writer.close()
            }
    }





    fun release() {
        recorder?.reset()
        recorder?.release()
        recorder = null
    }
}
