package com.example.mocklocationserver.web

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import com.example.mocklocationserver.web.mocklocation.MockLocationUtility
import com.example.mocklocationserver.web.service.MockLocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class MainActivityViewModel(
    app: Application
): AndroidViewModel(app) {

    sealed class UIState {
        data class PermissionNotGranted(
            val setMockLocationApp: Boolean,
            val notGranted: List<String>
        ): UIState()

        object ReadyToStartService: UIState()
    }

    private val _uiState = MutableStateFlow<UIState?>(null)
    val uiState = _uiState.asStateFlow()


    companion object {
        val permissionList = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    fun checkPermissionGranted() {
        val app = getApplication<Application>()

        val notGranted = permissionList
            .associateWith { app.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
            .filter { it.value == false }
            .keys
            .toList()

        val setMockLocationApp = MockLocationUtility.isEnabledMockLocationApp(app)

        _uiState.value = when {
            setMockLocationApp == false || notGranted.isNotEmpty() ->
                UIState.PermissionNotGranted(
                    setMockLocationApp = setMockLocationApp,
                    notGranted = notGranted
                )
            else -> UIState.ReadyToStartService
        }
    }


    fun startWebServer() {
        val context = getApplication() as Context
        val i = Intent(context, MockLocationService::class.java)
        context.startService(i)
    }


    fun stopWebServer() {
        val context = getApplication() as Context
        val i = Intent(context, MockLocationService::class.java)
        context.stopService(i)
    }


    fun showDevelopmentSettings() {
        val context = getApplication() as Context
        MockLocationUtility.goApplicationDevelopmentSettings(context)
    }
}