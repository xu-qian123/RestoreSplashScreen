package com.gswxxn.restoresplashscreen.hook

import android.util.Log
import com.gswxxn.restoresplashscreen.data.DataConst
import com.gswxxn.restoresplashscreen.utils.HookPrefs
import io.github.libxposed.api.XposedModule

/** Hook 侧日志, 受 ENABLE_LOG + 24h 开关控制 */
class HLog(private val module: XposedModule, private val prefs: HookPrefs) {
    fun i(vararg msg: String) {
        try {
            if (!prefs.get(DataConst.ENABLE_LOG)) return
            if (System.currentTimeMillis() - prefs.get(DataConst.ENABLE_LOG_TIMESTAMP) > 86400000) return
            msg.forEach {
                try {
                    android.util.Log.i("RestoreSplashScreen", it)
                } catch (_: Throwable) {
                }
                try {
                    module.log(android.util.Log.INFO, "RestoreSplashScreen", it)
                } catch (_: Throwable) {
                    android.util.Log.i("RestoreSplashScreen", it)
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun e(msg: String, t: Throwable) {
        try {
            android.util.Log.e("RestoreSplashScreen", msg, t)
        } catch (_: Throwable) {
        }
        try {
            module.log(android.util.Log.ERROR, "RestoreSplashScreen", "$msg: ${t.message}")
        } catch (_: Throwable) {
            android.util.Log.e("RestoreSplashScreen", msg, t)
        }
    }
}
