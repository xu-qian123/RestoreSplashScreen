package com.gswxxn.restoresplashscreen.hook

import com.gswxxn.restoresplashscreen.data.DataConst
import com.gswxxn.restoresplashscreen.utils.HookPrefs

/**
 * 当前启动应用会话状态 (替代旧 GenerateHookHandler 的静态缓存)
 *
 * 由 makeSplashScreenContentView 写入, removeStartingWindow 消费后重置
 */
object Session {
    var packageName: String = ""
    var activity: String = ""
    var except: Boolean = false
    var hooking: Boolean = false

    fun reset() {
        packageName = ""
        activity = ""
        except = false
        hooking = false
        IconState.reset()
        BgState.reset()
    }

    fun isExcept(pkg: String, prefs: HookPrefs): Boolean {
        if (pkg.isBlank()) return true
        val list = prefs.get(DataConst.CUSTOM_SCOPE_LIST)
        val isExceptionMode = prefs.get(DataConst.IS_CUSTOM_SCOPE_EXCEPTION_MODE)
        return prefs.get(DataConst.ENABLE_CUSTOM_SCOPE) &&
            ((isExceptionMode && (pkg in list)) || (!isExceptionMode && pkg !in list))
    }
}

/** 图标处理会话缓存 */
object IconState {
    var dominantColor: Int? = null
    var needShrink: Boolean = false
    var useBigMiuiIcon: Boolean? = null
    var iconDrawable: android.graphics.drawable.Drawable? = null

    fun reset() {
        dominantColor = null
        needShrink = false
        useBigMiuiIcon = null
        iconDrawable = null
    }
}

/** 背景处理会话缓存 */
object BgState {
    var tmpAttrs: Any? = null

    fun reset() {
        tmpAttrs = null
    }
}
