package com.hyperana.audiolooper


import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecordingConfiguration
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_btlatency.*
import kotlinx.android.synthetic.main.content_btlatency.*
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class BTLatencyActivity : AppCompatActivity() ,  MetronomeViewHolder.Listener {

    val TAG = "BTLatencyActivity"

    val BPMINUTE = 120
    val BPMEASURE = 8


    var FILENAME = "calibration"

    var audioManager: AudioManager? = null
    private val executor = Executors.newScheduledThreadPool(2)

    internal class ThreadPerTaskExecutor : Executor {
        override fun execute(r: Runnable) {
            Thread(r).start()
        }
    }


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
            return dataDirectory?.let {dir ->
                dir.listFiles()?.find { it.nameWithoutExtension.equals(FILENAME) }
                    ?: File(dir, FILENAME)
            }
        }

    var metronome: MetronomeViewHolder? = null
    var recorder: Recorder? = null

    // player is set on create, and after recording stopped, nulled on stop()
    var player: SoundPoolPlayer? = null
        set(value) {
            field = value
            runOnUiThread {
                Log.d(TAG, "player set: $player")
                button_play?.isEnabled = (value != null)
            }
        }

    var sound = ToneGenerator(AudioManager.STREAM_MUSIC, 0)

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
    val MAX_LATENCY = 800
    val MIN_LATENCY = -200 // must be abs value < PLAYBACK_BEAT_TIME
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


    // *************************** CB's:
    val playbackConfigCB = object: AudioManager.AudioPlaybackCallback() {

        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            super.onPlaybackConfigChanged(configs)
            Log.d(TAG, "playback configs: ${configs?.joinToString()}")
        }
    }

    val recordingConfigCB = object: AudioManager.AudioRecordingCallback() {

        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            super.onRecordingConfigChanged(configs)
            Log.d(TAG, "recording configs: ${configs?.joinToString()}")
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



            val sampleRate = (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
                .also {
                    Log.d(TAG, "device output sample rate: $it")
                }
                .toIntOrNull()
                ?: APP_SAMPLE_RATE
                /*.apply {

                registerAudioPlaybackCallback(playbackConfigCB, mHandler)
                registerAudioRecordingCallback(recordingConfigCB, mHandler)
            }*/

            findViewById<ViewGroup>(R.id.metronome_frame)?.also {
                metronome = MetronomeViewHolder(it, BPMEASURE, BPMINUTE, this)
            }


            audioDirectory = getExternalFilesDir(AUDIO_DIR_NAME)?.let {
                if (!it.exists() && !it.mkdirs()) {
                    Log.e(TAG, "failed create data directory")
                    null
                } else it
            }
            dataDirectory = getExternalFilesDir(DATA_DIR_NAME)?.let {
                if (!it.exists() && !it.mkdirs()) {
                    Log.e(TAG, "failed create data directory")
                    null
                } else it
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

            recorder = Recorder(audioDirectory!!, dataDirectory!!, sampleRate)
            player = audioFile?.let { SoundPoolPlayer(applicationContext, it, dataFile?.let { parseFileMetadata(it) }) }

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

        if (state == State.PREROLL) {

            // start recording before target measure:
            if ((measure == 0) && (beat == 0)) {

                // schedule start of recording relative to next measure start:
                executor.schedule(
                    {
                        recorder?.begin()
                    },
                    (BPMEASURE * 60 * 1000L/BPMINUTE) - APP_RECORD_LEAD,
                    TimeUnit.MILLISECONDS
                )
                Log.d(TAG, "recording scheduled")
            }

            // mark actual start of target measure:
            if ((beat == 0) && (measure == 1)) {
                recorder?.markStart()
                metronome?.setTitle("RECORDING")
                state = State.RECORDING
            }
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

        metronome?.setTitle(null)

        state = State.NONE
    }


    fun startRecording() {
        try {
            Log.d(TAG, "startRecording")
            stop()

            // remove playback of old recording:
            // todo: not necessary with soundpool
            player?.release()
            player = null

            // prepare new recording:
            state = State.PREROLL
            recorder?.prepareFile(FILENAME)

            // start beats:
            metronome?.setTitle("GET READY")
            metronome?.start()


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
                try {
                    dataFile!!.also {
                        saveFileMetadata(it, data)
                        Log.d(TAG, "metadata saved: ${it.absolutePath}")
                    }
                }
                catch (e: Exception) {
                    Log.e(TAG, "failed save metadata", e)
                    Toast.makeText(this, "Failed to save recording", Toast.LENGTH_LONG).show()
                }
            }

        stop()

        // todo: player could be controlled by a file observer
        audioFile?.also {
            player?.release()
            player = SoundPoolPlayer(applicationContext, it, dataFile?.let {parseFileMetadata(it)})

            state = State.CAN_PLAY
        }


    }




    fun startPlaying() {
        Log.d(TAG, "startPlaying")

        try {

            // delay first play to allow negative latency adjustment
            // repeat every measure
            player!!.startRepeatedPlayback(APP_PLAYBACK_LEAD, 60 * 1000L * BPMEASURE / BPMINUTE)

            // schedule metronome delayed start
            metronome!!.apply {
                setTitle("PLAYING")
                start(APP_PLAYBACK_LEAD + latency.toLong())
            }

            Log.d(TAG, "schedule mediaplayer $APP_PLAYBACK_LEAD and metronome ${APP_PLAYBACK_LEAD + latency}")

            state = State.PLAYING


        }
        catch (e: Exception) {
            Log.e(TAG, "failed start playing", e)
        }

    }

    fun stopPlaying() {
        Log.d(TAG, "stopPLaying")
        stop()
        state = State.CAN_PLAY
    }



}
