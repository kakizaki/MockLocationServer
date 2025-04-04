package com.example.mocklocationserver.web

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.mocklocationserver.web.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>

    private var alertDialog: AlertDialog? = null

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.viewModel = viewModel
        setContentView(binding.root)

        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { isGranted ->
                val notGranted = isGranted.filter { it.value == false }.keys.toTypedArray()
                if (notGranted.isNotEmpty()) {
                    showRequestPermission(notGranted)
                }
            }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.checkPermissionGranted()
                viewModel.uiState.collect { updateUIState(it) }
           }
        }
    }


    override fun onResume() {
        super.onResume()

        alertDialog?.let {
            it.dismiss()
            alertDialog = null
        }
    }


    private fun updateUIState(state: MainActivityViewModel.UIState?) {
        when (state) {
            null -> {
                binding.startService.isEnabled = false
            }
            is MainActivityViewModel.UIState.PermissionNotGranted -> {
                binding.startService.isEnabled = false
                if (state.notGranted.isNotEmpty()) {
                    showRequestPermission(state.notGranted.toTypedArray())
                } else if (state.setMockLocationApp == false) {
                    showAlertOfNeedSetMockLocationApp()
                }
            }
            is MainActivityViewModel.UIState.ReadyToStartService -> {
                binding.startService.isEnabled = true
            }
        }
    }


    private fun showRequestPermission(notGranted: Array<String>) {
        for (it in notGranted) {
            if (shouldShowRequestPermissionRationale(it)) {
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
                alert.show()

                alertDialog = alert
                return
            }
        }

        requestPermissionLauncher.launch(notGranted)
    }


    private fun showAlertOfNeedSetMockLocationApp() {
        alertDialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_not_set_mocklocation_app)
            .setMessage(R.string.dialog_show_development_options)
            .setPositiveButton(R.string.button_yes) { dialog, which ->
                viewModel.showDevelopmentSettings()
            }
            .setNegativeButton(R.string.button_no) { dialog, which ->
            }
            .show()
    }

}
