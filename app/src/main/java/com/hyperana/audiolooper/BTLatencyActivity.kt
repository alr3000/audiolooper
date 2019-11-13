package com.hyperana.audiolooper

import android.content.Context
import android.media.*
import android.media.MediaPlayer.SEEK_CLOSEST_SYNC
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.JsonWriter
import android.util.Log
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity;

import kotlinx.android.synthetic.main.activity_btlatency.*
import kotlinx.android.synthetic.main.content_btlatency.*
import org.json.JSONObject
import java.io.File
import java.util.*
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class BTLatencyActivity : AppCompatActivity() , MediaRecorder.OnInfoListener, MediaRecorder.OnErrorListener, MetronomeViewHolder.Listener {

    val TAG = "BTLatencyActivity"
    val PREF_BT_LATENCY = "bluetooth_latency"
    val DATA_RECORD_LEAD = "recordLead"

    val DATA_DIR = "metadata_"

    val BPMINUTE = 120
    val BPMEASURE = 8

    var metronome: MetronomeViewHolder? = null
    var measure = 0

    var FILENAME = "calibration"
    var FORMAT_EXT = ".3gp"
    var recorder: MediaRecorder? = null
    var recordStartTime: Long? = null
    var recordLeadTime: Long? = null

    var isAlt = false
    var player: MediaPlayer? = null
    var altPlayer: MediaPlayer? = null
    var nextPlayer: MediaPlayer? = null
    get() {
        isAlt = !isAlt
        return if (isAlt) altPlayer else player
    }
    val PLAYBACK_BEAT_TIME = 500L // ms after user start to align first beat
    var playTimer: ScheduledThreadPoolExecutor? = null

    var latency = 0
        set(value) {
            field = value
            button_reset?.isEnabled = true

            // if currently playing, reset to apply new latency value
            player?.also {
                stopPlaying()
                startPlaying()
            }
        }
    val MAX_LATENCY = 500
    val MIN_LATENCY = -500 // must be abs value < PLAYBACK_BEAT_TIME
    val LATENCY_FACTOR = 100.0/(MAX_LATENCY - MIN_LATENCY)

    enum class State {
        PREROLL,
        RECORDING,
        CAN_PLAY,
        PLAYING,
        NONE
    }
    var state: State = State.NONE
        set(value) {
            field = value
            Log.d(TAG, "state = $value")
            metronome?.apply {

                container.isSelected = (value == State.PREROLL)
                container.isActivated = (value == State.RECORDING)

                if (value == State.NONE) {
                    progress.progress = 0
                    measure = 0
                }
                if (value == State.RECORDING) progress.isActivated = true
            }
            button_record?.apply {
                isSelected = (value == State.PREROLL)
                isActivated = (value == State.RECORDING)
                isChecked = (value == State.PREROLL || value == State.RECORDING)
            }

            button_play?.apply {
                isEnabled = externalCacheDir?.list()?.contains(FILENAME + FORMAT_EXT) == true
                isChecked = (value == State.PLAYING)
                isActivated = (value == State.PLAYING)
            }

            button_save?.apply {
                isEnabled = true
            }
      }

    // *************************** Lifecycle:

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_btlatency)
        setSupportActionBar(toolbar)

        fab.hide()
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.title_activity_btlatency)
        }

        findViewById<ViewGroup>(R.id.metronome_frame)?.also {
            metronome = MetronomeViewHolder(it, BPMEASURE, BPMINUTE, this)
        }

        button_record?.setOnCheckedChangeListener { compoundButton, b ->
            try {
                if (b) {
                    startRecording()
                }
                else {
                    stop()
                }
            }
            catch (e: Exception) {
                Log.e(TAG, "failed start record", e)
            }
        }

        button_play?.setOnCheckedChangeListener { compoundButton, b ->
            if (b) startPlaying()
            else stopPlaying()
        }

        button_reset?.setOnClickListener {
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .getInt(PREF_BT_LATENCY, 0).also {
                    latency_slider?.progress = ((it - MIN_LATENCY) * LATENCY_FACTOR).toInt()
                    latency_text?.setText(it.toString())
                    latency = it
                }
            it.isEnabled = false
        }
        button_reset?.performClick()

        latency_slider?.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                p0?.progress?.div(LATENCY_FACTOR)?.toInt()?.also {
                    latency = it + MIN_LATENCY
                    latency_text?.setText(latency.toString())
                }
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {

            }

            override fun onStartTrackingTouch(p0: SeekBar?) {

            }
        })

        button_save?.setOnClickListener {
            PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
                .putInt(PREF_BT_LATENCY, latency.coerceIn(MIN_LATENCY .. MAX_LATENCY))
                .apply()
            finish()
        }


        state = State.NONE

    }

    override fun onPause() {
        super.onPause()
        stop()
    }

    override fun onResume() {
        super.onResume()

       setFileMetadata()

    }

    //********************************************** Listeners:

    // recorder records first two full measures and marks beginning of the second measure:
    override fun onBeat(beat: Int, measure: Int) {
        Log.d(TAG, "onbeat $beat ($measure)")

        if ((state == State.PREROLL) && (beat == BPMEASURE - 1) && (measure == 0)) {

                // record trimmable length:
                recordLeadTime = recordStartTime?.let { System.currentTimeMillis() - it} ?: 0L
                state = State.RECORDING
        }
        else if ((state == State.RECORDING) && (measure == 2)) {

                // stop recording:
                stopRecording()


        }
    }

    override fun onInfo(p0: MediaRecorder?, p1: Int, p2: Int) {
        Log.d(TAG, "on recorder info: $p1, $p2")
        if (p1 == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            //todo:alert
        }
    }

    override fun onError(p0: MediaRecorder?, p1: Int, p2: Int) {
        Log.w(TAG, "on recorder error: $p1, $p2")
    }

    // ***************************************** Logic:

    fun stop() {
        Log.d(TAG, "stop")

        metronome?.stop()

        playTimer?.shutdownNow()
        playTimer = null

        recorder?.reset()
        recorder?.release()
        recorder = null

        player?.reset()
        player?.release()
        player = null

        altPlayer?.reset()
        altPlayer?.release()
        altPlayer = null

        state = State.NONE
    }






    fun startRecording() {
        Log.d(TAG, "startRecording")
        stop()

        // start recording immediately to catch first beat
        recordStartTime = null
        recordLeadTime = null

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile("${externalCacheDir!!.absolutePath}/calibration.3gp")
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC_ELD)
            setOnInfoListener(this@BTLatencyActivity)
            setMaxDuration(30*1000)
            setOnErrorListener(this@BTLatencyActivity)


            try {
                prepare()
                start()
                recordStartTime = System.currentTimeMillis()

                state = State.PREROLL

                metronome?.start()

                Log.d(TAG, "recording started")

            } catch (e: Exception) {
                Log.e(TAG, "prepare() failed")
            }



        }

    }


    fun stopRecording() {
        Log.d(TAG, "stopRecording")

        stop()
        state = State.CAN_PLAY

        try {
            saveFileData(
                FILENAME, mapOf(
                    DATA_RECORD_LEAD to recordLeadTime.toString()
                )
            )
        }
        catch (e: Exception) {
            Log.d(TAG, "failed save data", e)
        }
    }

    fun saveFileData(filename: String, map: Map<String, String>) {

        openFileOutput(DATA_DIR + filename, Context.MODE_PRIVATE).writer()
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

    fun setFileMetadata() {
        try {
            recordLeadTime = openFileInput(DATA_DIR + FILENAME).reader().readText().let { string ->
                JSONObject(string).getString(DATA_RECORD_LEAD).toLong()
            }
        } catch (e: Exception) {
            Log.d(TAG, "no record data found", e)
        }
    }


        // prepare player and altPlayer for alternating playback:
    fun startPlaying() {
        Log.d(TAG, "startPlaying")

        altPlayer = createMediaPlayer().apply {

            // prepare, but don't start:
            setOnPreparedListener { player ->
                Log.d(TAG, "mediaplayer2 prepared")
                recordLeadTime?.also {
                    player.seekTo(it, SEEK_CLOSEST_SYNC)
                    Log.d(TAG, "mediaplayer2  @seekTo: $it")
                }

            }
        }

        player = createMediaPlayer().apply {
            setOnBufferingUpdateListener { mediaPlayer, i ->  Log.d(TAG, "buffering update: $i")}
            //setOnMediaTimeDiscontinuityListener { mediaPlayer, mediaTimestamp ->  Log.d(TAG, "media discontinuity $mediaTimestamp")}
            setOnTimedTextListener { mediaPlayer, timedText ->  Log.d(TAG, "timedText: $timedText")}

            // when ready, seek, start playback, and set delayed start for metronome:
            setOnPreparedListener { player ->
                Log.d(TAG, "mediaplayer1 prepared")
                recordLeadTime?.also {
                    player.seekTo(it, SEEK_CLOSEST_SYNC)
                    Log.d(TAG, "mediaplayer1  @seekTo: $it")
                }

                startRepeatedPlayback()

            }

        }
  }
    fun startRepeatedPlayback() {

        playTimer?.shutdownNow()

        playTimer = ScheduledThreadPoolExecutor(4).apply {

            // schedule repeated alternating plays
            scheduleAtFixedRate(
                object: TimerTask() {

                    override fun run() {
                        nextPlayer?.start()
                        Log.d(TAG, "mediaplayer started")
                    }
                },
                PLAYBACK_BEAT_TIME, // delay first play to allow negative latency adjustment
                60*1000L*BPMEASURE/BPMINUTE, // repeat every measure
                TimeUnit.MILLISECONDS
            )

            // schedule metronome delayed start
            schedule(
                object: TimerTask() {

                    override fun run() {
                        metronome?.start()
                    }
                },
                PLAYBACK_BEAT_TIME + latency.toLong(), // delay start and adjust relative to player start
            TimeUnit.MILLISECONDS
            )
        }


        Log.d(TAG, "schedule mediaplayer $PLAYBACK_BEAT_TIME and metronome ${PLAYBACK_BEAT_TIME + latency}")

        state = State.PLAYING
    }

    fun createMediaPlayer() : MediaPlayer {
        return MediaPlayer.create(applicationContext, Uri.fromFile(File(externalCacheDir!!, FILENAME + FORMAT_EXT))).apply {
            setOnErrorListener { mediaPlayer, i, i2 ->
                Log.w(TAG, "mediaplayer error $i, $i2")
                //todo: alert
                stopPlaying()
                true // oncompletionhandler will not be called
            }
            setOnInfoListener { mediaPlayer, i, i2 ->
                Log.d(TAG, "mediaplayer info $i, $i2")
                true
            }

            // on completion, seek again to be ready for next play
            setOnCompletionListener { thisPlayer ->
                Log.d(TAG, "player complete")
                recordLeadTime?.also {
                    thisPlayer.seekTo(it, SEEK_CLOSEST_SYNC)
                    Log.d(TAG, "mediaplayer  @seekedTo: $it")
                }

            }

        }
    }

    fun stopPlaying() {
        Log.d(TAG, "stopPLaying")
        stop()
        state = State.CAN_PLAY
    }

}
