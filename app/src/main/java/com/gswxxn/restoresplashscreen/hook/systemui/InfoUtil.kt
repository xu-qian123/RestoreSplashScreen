package com.gswxxn.restoresplashscreen.hook.systemui

import android.content.ComponentName
import com.gswxxn.restoresplashscreen.hook.HookKit
import com.gswxxn.restoresplashscreen.hook.Session
import com.gswxxn.restoresplashscreen.utils.fld

/** 从 StartingWindowInfo 解析包名与 Activity */
fun pkgFromStartingInfo(info: Any?): Pair<String, String> {
    if (info == null) return "" to ""
    return try {
        val taskInfo = info.fld("taskInfo")
        val activityInfo = info.fld("targetActivityInfo")
        val taskTopInfo = taskInfo?.fld("topActivityInfo")
        val taskTop = taskInfo?.fld("topActivity") as? ComponentName
        val pkg = (activityInfo?.fld("packageName") as? String)
            ?: (taskTopInfo?.fld("packageName") as? String)
            ?: taskTop?.packageName
            ?: (info.fld("mlaunchPackageName") as? String)
            ?: ""
        // 实际启动组件必须取 ActivityInfo.name (alias 会保留):
        // - targetActivityInfo 可能为 null, 此时 SplashscreenContentDrawer 会回退用 taskInfo.topActivityInfo
        // - taskInfo.topActivity 是 realActivity, 拨号 alias 会被解析成 PeopleActivity, 导致图标混淆
        val activity = (activityInfo?.fld("name") as? String)
            ?.takeIf { it.isNotBlank() }
            ?: (taskTopInfo?.fld("name") as? String)?.takeIf { it.isNotBlank() }
            ?: taskTop?.className
            ?: ""
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
