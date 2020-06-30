package com.example.mocklocationserver.web

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        start_service.setOnClickListener { startWebServer() }
        stop_service.setOnClickListener { stopWebServer() }
    }


    fun startWebServer() {
        var i = Intent(this, MockLocationService::class.java)
        startService(i)
    }


    fun stopWebServer() {
        var i = Intent(this, MockLocationService::class.java)
        stopService(i)
    }
}
