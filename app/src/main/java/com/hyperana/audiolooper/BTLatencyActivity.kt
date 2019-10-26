package com.hyperana.audiolooper

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity;

import kotlinx.android.synthetic.main.activity_btlatency.*
import kotlinx.android.synthetic.main.content_btlatency.*
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

class BTLatencyActivity : AppCompatActivity() {

    val TAG = "BTLatencyActivity"
    val BPMINUTE = 70
    val BPMEASURE = 4
    val PROGRESS_INTERVAL = 40L //ms

    var metronome: MetronomeViewHolder? = null

    var beatTimer: Timer? = null
    var progressTimer: Timer? = null

    var measure = 0
    enum class State {
        PREROLL,
        RECORDING,
        PLAYING,
        NONE
    }
    var state: State = State.NONE
        set(value) {
            field = value
            metronome?.apply {
                color = when (value) {
                    State.PREROLL -> Color.YELLOW
                    State.RECORDING -> Color.GREEN
                    else -> Color.GRAY
                }
                container.isSelected = (value != State.NONE)
                container.isActivated = (value == State.RECORDING)

                if (value == State.NONE) progress.progress = 0
                if (value == State.RECORDING) progress.isActivated = true
            }
        }

    class MetronomeViewHolder(val container: ViewGroup, val bpMeasure: Int, val bpMinute: Int) {
        val TAG = "MetronomeVH"

        var beats = listOf<View>()

        val progress = container.findViewById<ProgressBar>(R.id.metronome_progress)
        val progressFactor = 100.0/bpMeasure
        val progressOffset = 50/bpMeasure

        var color = Color.GRAY
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

        fun advance() : Int {
            index = (index + 1)%bpMeasure

           // progress.setProgress((index*progressFactor + progressOffset).toInt())

                return index

        }
    }


    inner class BeatTask : TimerTask() {
        override fun run() {
            runOnUiThread {
                try {
                    Log.d(TAG, "beatTask")
                    if (metronome?.advance() == 0) {
                        nextMeasure()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "beatTask failed", e)
                }
            }
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

        button_save?.isEnabled = false
        button_play?.isEnabled = false


    }

    override fun onPause() {
        super.onPause()
        stop()
    }



    fun stop() {
        measure = 0
        beatTimer?.cancel()
        beatTimer = null
        progressTimer?.cancel()
        progressTimer = null

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

        beatTimer = Timer().apply {
           // scheduleAtFixedRate(BeatTask(), period, period)
        }
        progressTimer = Timer().apply {
            scheduleAtFixedRate(ProgressTask(), PROGRESS_INTERVAL, PROGRESS_INTERVAL)
        }

    }

    fun startRecording() {
        state = State.RECORDING
    }

    fun stopRecording() {
        stop()
        state = State.NONE
    }

}
