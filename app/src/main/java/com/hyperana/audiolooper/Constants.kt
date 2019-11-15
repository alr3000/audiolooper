package com.hyperana.audiolooper

import android.util.JsonWriter
import android.util.Log
import org.json.JSONObject
import java.io.File

// audio file metadata keys:
val DATA_RECORD_LEAD = "recordLeadTimeMs"
val DATA_BPMEASURE = "beatsPerMeasure"
val DATA_BPMINUTE = "beatsPerMinute"

// preferences keys:
val PREF_BT_LATENCY = "bluetooth_latency"

// app constants:
val AUDIO_DIR_NAME = "audio"
val DATA_DIR_NAME = "data"

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