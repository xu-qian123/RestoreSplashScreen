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
                    module.log(Log.INFO, "RestoreSplashScreen", it)
                } catch (_: Throwable) {
                    Log.i("RestoreSplashScreen", it)
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun e(msg: String, t: Throwable) {
        try {
            module.log(Log.ERROR, "RestoreSplashScreen", "$msg: ${t.message}")
        } catch (_: Throwable) {
            Log.e("RestoreSplashScreen", msg, t)
        }
    }
}
