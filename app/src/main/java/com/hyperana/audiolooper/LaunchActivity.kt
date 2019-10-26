package com.hyperana.audiolooper

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class LaunchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)

        startActivity(Intent(this, BTLatencyActivity::class.java))
    }
}
