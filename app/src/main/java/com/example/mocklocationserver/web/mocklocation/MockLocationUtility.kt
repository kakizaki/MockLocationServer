package com.example.mocklocationserver.web.mocklocation

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.core.app.AppOpsManagerCompat

class MockLocationUtility {
    companion object {

        /**
         * 仮の位置情報をセットするアプリケーションとして設定されているか
         */
        fun isEnabledMockLocationApp(c: Context): Boolean {
            try {
                val r = AppOpsManagerCompat.noteOp(
                    c,
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    c.packageName
                )
                return r == AppOpsManager.MODE_ALLOWED
            }
            catch (e: SecurityException) {
                return false
            }
            return false
        }

        /**
         * 開発者設定を表示する
         */
        fun goApplicationDevelopmentSettings(c: Context) {
            c.startActivity(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

    }

}


