package com.example.mocklocationserver.web

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    companion object {
        val REQUESTCODE_PERMISSION_LOCATION = 1

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        start_service.setOnClickListener { startWebServer() }
        stop_service.setOnClickListener { stopWebServer() }
    }


    override fun onResume() {
        super.onResume()

        checkLocationPermission()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUESTCODE_PERMISSION_LOCATION) {
            checkLocationPermission()
        }
    }


    fun checkLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION))  {
            // パーミッションの許可の際に "今後、表示しない" にチェックを付けた場合
            val alert = AlertDialog.Builder(this)
                .setTitle(R.string.permission_dialog_title)
                .setMessage(R.string.permission_dialog_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }
                .create()
                .show()
        }
        else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUESTCODE_PERMISSION_LOCATION)
        }
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
