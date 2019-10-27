package com.hyperana.audiolooper

import android.media.MediaRecorder
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity;

import kotlinx.android.synthetic.main.activity_btlatency.*
import kotlinx.android.synthetic.main.content_btlatency.*
import java.util.*
import kotlin.math.floor

class BTLatencyActivity : AppCompatActivity() , MediaRecorder.OnInfoListener {

    val PREF_BT_LATENCY = "bluetooth_latency"

    val TAG = "BTLatencyActivity"
    val BPMINUTE = 70
    val BPMEASURE = 4
    val PROGRESS_INTERVAL = 40L //ms

    var metronome: MetronomeViewHolder? = null
    var progressTimer: Timer? = null
    var measure = 0

    var recorder: MediaRecorder? = null
    var filename = "" //set after create for directory


    var latency = 0
    set(value) {
        field = value
        button_reset?.isEnabled = true
    }
    val MAX_LATENCY = 1000
    val LATENCY_FACTOR = 100.0/MAX_LATENCY

    enum class State {
        PREROLL,
        RECORDING,
        CAN_PLAY,
        NONE
    }
    var state: State = State.NONE
        set(value) {
            field = value
            metronome?.apply {

                container.isSelected = (value != State.NONE)
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
                isEnabled = (value == State.CAN_PLAY)
            }

            button_save?.apply {
                isEnabled = true
            }



        }

    class MetronomeViewHolder(val container: ViewGroup, val bpMeasure: Int, val bpMinute: Int) {
        val TAG = "MetronomeVH"

        var beats = listOf<View>()

        val progress = container.findViewById<ProgressBar>(R.id.metronome_progress)
        val progressFactor = 100.0/bpMeasure
        val progressOffset = 50/bpMeasure

           var index = 0

        init {
            container.findViewById<LinearLayout>(R.id.metronome_beat_box)?.also {box->
                box.removeAllViews()

                beats = (1 .. bpMeasure).map { num ->
                    LayoutInflater.from(container.context).inflate(R.layout.beat, box, false).also {
                        Log.d(TAG, "beat view #$num")
                        it.findViewById<TextView>(R.id.text)?.also {text ->
                            text.text = num.toString()
                            text.textSize = 40*4/bpMeasure.toFloat()
                            box.addView(it)
                        }
                    }
                }
            }
        }

        fun setProgress(ms: Long) : Int {
            (ms*bpMinute).toDouble().div(60*1000.0).also { beatFraction ->
                (beatFraction * 100.0/bpMeasure).also { percent ->

                    progress.setProgress(percent.toInt() % 100)
                    if (floor(beatFraction).toInt()%bpMeasure != index) {
                        advance()
                    }
                    Log.d(TAG, "ms: $ms, beatFraction: $beatFraction,  $percent%, index: $index")

                    return index
                }
            }
        }

        fun advance(){
            index = (index + 1)%bpMeasure
            //todo: flash beat background
       }
    }



    inner class ProgressTask : TimerTask() {
        val start = System.currentTimeMillis()
        var hasStartedMeasure = false
        override fun run() {
            runOnUiThread {
                try {
                    Log.d(TAG, "beatTask")
                    metronome?.setProgress(System.currentTimeMillis() - start).also { beat ->
                        if (beat != 0)
                            hasStartedMeasure = true
                        else if (hasStartedMeasure) {
                            hasStartedMeasure = false
                            nextMeasure()
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "beatTask failed", e)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_btlatency)
        setSupportActionBar(toolbar)

        fab.hide()
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.title_activity_btlatency)
        }

        //todo: make this cache file
        filename = "${externalCacheDir!!.absolutePath}/calibration.3gp"


        findViewById<ViewGroup>(R.id.metronome_frame)?.also {
            metronome = MetronomeViewHolder(it, BPMEASURE, BPMINUTE)
        }

        button_record?.setOnClickListener {
            try {
                startCountdown()
            }
            catch (e: Exception) {
                Log.e(TAG, "failed start record", e)
            }
        }

        button_reset?.setOnClickListener {
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .getInt(PREF_BT_LATENCY, 0).also {
                    latency_slider?.progress = (it * LATENCY_FACTOR).toInt()
                }
            it.isEnabled = false
        }

        button_reset?.performClick()

        latency_slider?.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                p0?.progress?.div(LATENCY_FACTOR)?.toInt()?.also {
                    latency = it
                    latency_text?.setText(it.toString())
                }
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {

            }

            override fun onStartTrackingTouch(p0: SeekBar?) {

            }
        })

        button_save?.setOnClickListener {
            PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
                .putInt(PREF_BT_LATENCY, latency.coerceIn(0 .. MAX_LATENCY))
            finish()
        }

       state = State.NONE

    }

    override fun onPause() {
        super.onPause()
        stop()
    }

    override fun onInfo(p0: MediaRecorder?, p1: Int, p2: Int) {
        Log.d(TAG, "onInfo: $p1, $p2")
        if (p1 == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            //todo:alert
        }
    }

    fun stop() {
        progressTimer?.cancel()
        progressTimer = null

        recorder?.stop()
        recorder?.release()
        recorder = null

        state = State.NONE
    }

    fun nextMeasure() {
        when (state) {
            State.PREROLL -> {
                startRecording()
            }
            State.RECORDING -> {
                stopRecording()
            }
            else -> {}
        }
    }

    fun startCountdown() {
        stop()

        val period = 60*1000L/BPMINUTE
        state = State.PREROLL

        progressTimer = Timer().apply {
            scheduleAtFixedRate(ProgressTask(), PROGRESS_INTERVAL, PROGRESS_INTERVAL)
        }

    }

    fun startRecording() {
        Log.d(TAG, "startRecording: ${System.currentTimeMillis()}")
        state = State.RECORDING
        //todo: record audio to temp file

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(filename)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOnInfoListener(this@BTLatencyActivity)
            setMaxDuration(30*1000)

            try {
                prepare()
            } catch (e: Exception) {
                Log.e(TAG, "prepare() failed")
            }

            start()
        }

    }

    fun stopRecording() {
        stop()
        state = State.CAN_PLAY
    }

}
