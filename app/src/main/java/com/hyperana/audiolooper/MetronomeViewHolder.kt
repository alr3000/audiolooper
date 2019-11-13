package com.hyperana.audiolooper

import android.animation.ObjectAnimator
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.util.*

class MetronomeViewHolder(val container: ViewGroup, val bpMeasure: Int, val bpMinute: Int, val listener: Listener? = null) {
    val TAG = "MetronomeVH"
    val ANIMATION_DURATION = 100L
    var beatInterval = 60*1000L/bpMinute //ms


    var beats = listOf<View>()

    val progress = container.findViewById<ProgressBar>(R.id.metronome_progress)
    val sound = ToneGenerator(AudioManager.STREAM_MUSIC, 50)

    val progressAnimator: ObjectAnimator = ObjectAnimator.ofInt(progress, "progress", 0, 100).apply {
        setDuration(beatInterval * bpMeasure)
        interpolator = LinearInterpolator()
    }

    val highlightColor = container.context.getColor(R.color.colorBKGDHighlight)
    val flashColor = Color.CYAN

    var beatTimer: Timer? = null


    interface Listener {
        fun onBeat(beat: Int, measure: Int)
    }

    inner class BeatTask: TimerTask() {
        var isFirst = true

        var beatIndex = 0
        var measureIndex = 0

        override fun run() {
            container.post {
                if (isFirst) {
                    beatIndex = 0
                    measureIndex = 0
                    isFirst = false
                }
                else {
                    beatIndex = (beatIndex + 1) % bpMeasure
                    if (beatIndex == 0) measureIndex++
                }

                if (beatIndex == 0) {
                    progressAnimator.start()
                }
                playBeat(beatIndex)

                listener?.onBeat(beatIndex, measureIndex)
            }
        }
    }




    init {
        container.findViewById<LinearLayout>(R.id.metronome_beat_box)?.also { box->
            box.removeAllViews()

            beats = (1 .. bpMeasure).map { num ->
                LayoutInflater.from(container.context).inflate(R.layout.beat, box, false).also {
                    Log.d(TAG, "beat view #$num")
                    it.findViewById<TextView>(R.id.text)?.also { text ->
                        text.text = num.toString()
                        text.textSize = 40*4/bpMeasure.toFloat()
                        box.addView(it)
                    }
                }
            }
        }
    }


    fun start() {
        Log.d(TAG, "startMetronome")

        beatTimer?.cancel()
        beatTimer = Timer().apply {
            scheduleAtFixedRate(BeatTask(), beatInterval, beatInterval)
        }
    }

    fun stop() {
        beatTimer?.cancel()
        beatTimer = null

        progressAnimator.pause()
        progress.progress = 0
        beats.forEach {
            it.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun playBeat(num: Int) {
        Log.d(TAG, "*beat $num*")

        // play beat sound
        sound.startTone(ToneGenerator.TONE_PROP_BEEP)

        // flash beat background
        beats.forEachIndexed { i, v ->
            if (i == num)
                createBackgroundColorAnimator(v).start()
            else
                v.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun createBackgroundColorAnimator(view: View) : ObjectAnimator {
        return ObjectAnimator.ofArgb(view, "backgroundColor", highlightColor, flashColor).apply {

            setDuration(ANIMATION_DURATION/2)
            repeatCount = 1
            repeatMode = ObjectAnimator.REVERSE
        }
    }



}
