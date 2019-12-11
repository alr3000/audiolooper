package com.hyperana.audiolooper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat

class LaunchActivity : AppCompatActivity() {

    val MENUITEM_BTLATENCY = "Bluetooth Calibration"

    // Requesting permission to RECORD_AUDIO
    private var permissionToRecordAccepted = false
    private var permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    //todo: audio track selection fragment with list generated from fileobserver and add action
    //todo: audio track recording fragment

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults.get(0) == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        if (!permissionToRecordAccepted) {
            AlertDialog.Builder(this).apply {
                setMessage(R.string.no_record_permission_alert)
                setCancelable(true)
                setOnDismissListener {
                    startActivity(Intent(this.context, BTLatencyActivity::class.java))
                }
                create()
            }.show()
        }
        else {
            startActivity(Intent(this, BTLatencyActivity::class.java))
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)


    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {

        menu?.add(MENUITEM_BTLATENCY)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.title) {
            MENUITEM_BTLATENCY -> {
                startActivity(Intent(this, BTLatencyActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
