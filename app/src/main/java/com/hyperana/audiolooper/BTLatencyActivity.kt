package com.hyperana.audiolooper


import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity;

import kotlinx.android.synthetic.main.activity_btlatency.*
import kotlinx.android.synthetic.main.content_btlatency.*
import java.io.File

class BTLatencyActivity : AppCompatActivity() ,  MetronomeViewHolder.Listener {

    val TAG = "BTLatencyActivity"

    val BPMINUTE = 120
    val BPMEASURE = 8

    val PLAYBACK_BEAT_TIME = 500L // ms after user start to align first beat with metronome

    var FILENAME = "calibration"

    @Volatile
    var audioDirectory: File? = null
    val audioFile: File?
        get() {
            Log.d(TAG, "files: ${audioDirectory?.list()?.joinToString()}")
            return audioDirectory?.listFiles()?.find {
                    it.nameWithoutExtension.equals(FILENAME)
                }
        }
    @Volatile
    var dataDirectory: File? = null
    val dataFile: File?
        get() {
            return dataDirectory?.listFiles()?.find { it.nameWithoutExtension.equals(FILENAME) }
        }

    var metronome: MetronomeViewHolder? = null
    var recorder: Recorder? = null

    // player is set on create, and after recording stopped, nulled on stop()
    var player: Player? = null
        set(value) {
            field = value
            runOnUiThread {
                Log.d(TAG, "player set: $player")
                button_play?.isEnabled = (value != null)
            }
        }


    var latency = 0
        set(value) {
            field = value
            runOnUiThread {
                button_reset?.isEnabled = true
            }

            // if currently playing, reset to apply new latency value
            player?.also {
                stopPlaying()
                startPlaying()
            }
        }
    val MAX_LATENCY = 900
    val MIN_LATENCY = -100 // must be abs value < PLAYBACK_BEAT_TIME
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
                }
                if (value == State.RECORDING) progress.isActivated = true
            }
            button_record?.apply {
                isSelected = (value == State.PREROLL)
                isActivated = (value == State.RECORDING)
                isChecked = (value == State.PREROLL || value == State.RECORDING)
            }

            button_play?.apply {
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

        try {
            findViewById<ViewGroup>(R.id.metronome_frame)?.also {
                metronome = MetronomeViewHolder(it, BPMEASURE, BPMINUTE, this)
            }


            audioDirectory = getExternalFilesDir(AUDIO_DIR_NAME)?.let {
                if (!it.exists() && !it.mkdirs()) null else it
            }
            dataDirectory = getExternalFilesDir(DATA_DIR_NAME)?.let {
                if (!it.exists() && !it.mkdirs()) null else it
            }


            // not checkedchanged as only want to respond to user input
            button_record?.setOnClickListener { v ->
                try {
                    if ((v as ToggleButton).isChecked) {
                        startRecording()
                    } else {
                        cancelRecording()
                    }
                } catch (e: Exception) {
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

            // do this before setting listeners on the progress bar and before attaching player
            button_reset?.performClick()

            latency_slider?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
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
                    .putInt(PREF_BT_LATENCY, latency.coerceIn(MIN_LATENCY..MAX_LATENCY))
                    .apply()
                finish()
            }

            recorder = Recorder(audioDirectory!!, dataDirectory!!)
            player = audioFile?.let { Player(applicationContext, it, dataFile?.let { parseFileMetadata(it) }) }

        }
        catch (e: Exception) {
            Log.e(TAG, "failed oncreate", e)
        }

        state = State.NONE

    }

    override fun onPause() {
        super.onPause()
        stop()
    }

    override fun onResume() {
        super.onResume()

    }

    //********************************************** Listeners:

    // recorder records first two full measures and marks beginning of the second measure:
    override fun onBeat(beat: Int, measure: Int) {
        Log.d(TAG, "onbeat $beat ($measure)")

        if ((state == State.PREROLL) && (beat == BPMEASURE - 1) && (measure == 0)) {
            recorder?.markStart()
            state = State.RECORDING
        }
        else if ((state == State.RECORDING) && (measure == 2)) {

            // stop recording:
            stopRecording()


        }
    }



    // ***************************************** Logic:

    fun stop() {
        Log.d(TAG, "stop")

        metronome?.stop()
        player?.stop()
        recorder?.release()


        state = State.NONE
    }


    fun startRecording() {
        try {
            Log.d(TAG, "startRecording")
            stop()

            // remove playback of old recording:
            player?.release()
            player = null

            // start new:
            recorder?.start(FILENAME)
            state = State.PREROLL

            // start beats:
            metronome?.start()
            Log.d(TAG, "recording started")

        } catch (e: Exception) {
            Log.e(TAG, "prepare() failed")
        }


    }

    fun cancelRecording() {
        stop()
        audioFile?.delete()
    }


    fun stopRecording() {
        Log.d(TAG, "stopRecording")

        recorder?.stop()
            ?.plus(
                // add metadata
                mapOf(
                    DATA_BPMEASURE to BPMEASURE.toString(),
                    DATA_BPMINUTE to BPMINUTE.toString()
                )
            )
            ?.also { data ->
                saveFileMetadata(dataFile!!, data)
            }

        stop()

        // todo: player could be controlled by a file observer
        audioFile?.also {
            Log.d(TAG, "have audiofile")
            player?.release()
            player = Player(applicationContext, it, dataFile?.let {parseFileMetadata(it)})

            state = State.CAN_PLAY
        }


    }




    fun startPlaying() {
        Log.d(TAG, "startPlaying")

            // delay first play to allow negative latency adjustment
            // repeat every measure
            player?.startRepeatedPlayback(PLAYBACK_BEAT_TIME, 60 * 1000L * BPMEASURE / BPMINUTE)

            // schedule metronome delayed start
            metronome?.start(PLAYBACK_BEAT_TIME + latency.toLong())

            Log.d(TAG, "schedule mediaplayer $PLAYBACK_BEAT_TIME and metronome ${PLAYBACK_BEAT_TIME + latency}")

            state = State.PLAYING


    }

    fun stopPlaying() {
        Log.d(TAG, "stopPLaying")
        stop()
        state = State.CAN_PLAY
    }



}
