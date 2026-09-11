package com.gswxxn.restoresplashscreen.hook.systemui

import android.content.ComponentName
import com.gswxxn.restoresplashscreen.hook.HookKit
import com.gswxxn.restoresplashscreen.hook.Session
import com.gswxxn.restoresplashscreen.utils.fld

/** 从 StartingWindowInfo 解析包名与 Activity */
fun pkgFromStartingInfo(info: Any?): Pair<String, String> {
    if (info == null) return "" to ""
    return try {
        val activityInfo = info.fld("targetActivityInfo")
        val pkg = (activityInfo?.fld("packageName") as? String)
            ?: ((info.fld("taskInfo")?.fld("topActivity") as? ComponentName)?.packageName)
            ?: (info.fld("mlaunchPackageName") as? String)
            ?: ""
        val activity = (activityInfo?.fld("targetActivity") as? String) ?: ""
        pkg to activity
    } catch (_: Throwable) {
        "" to ""
    }
}

/** 读取 StartingWindowInfo 的窗口类型常量, 失败返回 AOSP 默认值 */
fun windowType(kit: HookKit, name: String, fallback: Int): Int {
    return try {
        val clazz = kit.findClass("android.window.StartingWindowInfo") ?: return fallback
        clazz.getDeclaredField(name).apply { isAccessible = true }.getInt(null)
    } catch (_: Throwable) {
        fallback
    }
}
