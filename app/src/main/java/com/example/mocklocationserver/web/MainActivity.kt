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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.mocklocationserver.web.databinding.ActivityMainBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    lateinit var requestPermissionLauncher: ActivityResultLauncher<String?>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startService.setOnClickListener { startWebServer() }
        binding.stopService.setOnClickListener { stopWebServer() }

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            onCheckLocationPermission(isGranted)
        }
    }


    override fun onResume() {
        super.onResume()

        checkLocationPermission()
    }

    fun checkLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            onCheckLocationPermission(true)
        } else {
            onCheckLocationPermission(false)
        }
    }

    fun onCheckLocationPermission(isGranted: Boolean) {
        if (isGranted) {
            // 現状、とくにすることがない
            return
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
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
            return
        }

        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }


    fun startWebServer() {
        val i = Intent(this, MockLocationService::class.java)
        startService(i)
    }


    fun stopWebServer() {
        val i = Intent(this, MockLocationService::class.java)
        stopService(i)
    }
}
