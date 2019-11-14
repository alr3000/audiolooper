package com.hyperana.audiolooper

import android.util.Log
import org.json.JSONObject
import java.io.File

val DATA_RECORD_LEAD = "recordLeadTimeMs"
val PREF_BT_LATENCY = "bluetooth_latency"
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