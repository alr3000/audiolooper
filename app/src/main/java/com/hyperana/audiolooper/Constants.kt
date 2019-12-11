package com.hyperana.audiolooper

import android.util.JsonWriter
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer

// audio file metadata keys:
val DATA_RECORD_LEAD = "recordLeadTimeMs"
val DATA_BPMEASURE = "beatsPerMeasure"
val DATA_BPMINUTE = "beatsPerMinute"

// preferences keys:
val PREF_BT_LATENCY = "bluetooth_latency"

// app constants:
val AUDIO_DIR_NAME = "audio"
val DATA_DIR_NAME = "data"
val APP_RECORD_LEAD = 100L //ms start record before first beat because it doesn't start immediately
val APP_PLAYBACK_LEAD = 1000L //ms delay play to allow match-up with recording and metronome according to calibration
val APP_SAMPLE_RATE = 44100

//************************* Helpers:

fun parseFileMetadata(file: File?) : Map<String, String>? {
    try {
        return file?.reader()?.readText()?.let { string -> JSONObject(string) }
            ?.let {json ->

                listOf(DATA_RECORD_LEAD)
                    .filter { json.has(it) }
                    .map{
                        Pair(it, json.get(it).toString())
                    }
                    .toMap()

            }
    } catch (e: Exception) {
        Log.d("parseFileMetadata", "no record data found", e)
        return null
    }
}

fun saveFileMetadata(file: File, map: Map<String, String>) {

    file.outputStream().writer()
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

fun loadFileToBuffer(file: File, bytes: ByteArray) : Boolean {
        var stream: InputStream? = null
        var result = false
        var index = 0
        try {
            stream  = file.inputStream()
            do {
                val k = stream.read(bytes, index, bytes.size - index)
                index = index + k
            } while (k != -1)
            result = true
        } catch (e: Exception) {
            Log.e("loadFileToBuffer", "failed read file", e)
        }
        finally {
            stream?.close()
            return result
    }
}