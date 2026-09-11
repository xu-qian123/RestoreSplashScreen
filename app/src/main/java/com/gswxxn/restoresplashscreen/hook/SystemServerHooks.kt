package com.gswxxn.restoresplashscreen.hook

import com.gswxxn.restoresplashscreen.data.DataConst
import com.gswxxn.restoresplashscreen.utils.call
import com.gswxxn.restoresplashscreen.utils.fldAs
import io.github.libxposed.api.XposedInterface

/**
 * system_server 相关 Hook (作用域 android)
 *
 * 目标类 com.android.server.wm.ActivityRecord, 方法签名以 HyperOS 4 services.jar 为准,
 * 按名称定位, 不写死参数个数
 */
class SystemServerHooks(private val kit: HookKit) {
    private val prefs get() = kit.prefs
    private val log get() = kit.log

    fun run() {
        val activityRecord = "com.android.server.wm.ActivityRecord"

        // 强制显示遮罩
        kit.hookMethod(activityRecord, "validateStartingWindowTheme", id = "validateTheme",
            hooker = XposedInterface.Hooker { chain ->
                val args = chain.args
                val pkgName = args.getOrNull(1) as? String ?: return@Hooker chain.proceed()
                val launchedFromSystemSurface = try {
                    (chain.thisObject?.call("launchedFromSystemSurface") as? Boolean) ?: false
                } catch (_: Throwable) {
                    false
                }
                val forceShow = prefs.get(DataConst.FORCE_SHOW_SPLASH_SCREEN) &&
                    pkgName in prefs.get(DataConst.FORCE_SHOW_SPLASH_SCREEN_LIST) &&
                    (!prefs.get(DataConst.REDUCE_SPLASH_SCREEN) || launchedFromSystemSurface)
                log.i("[Android] validateStartingWindowTheme(): ${if (forceShow) "" else "Not "}force show $pkgName")
                if (forceShow) true else chain.proceed()
            })

        // 彻底关闭 Splash Screen
        kit.hookMethod(activityRecord, "showStartingWindow", id = "showStarting",
            hooker = XposedInterface.Hooker { chain ->
                val pkgName = try {
                    chain.thisObject?.fldAs<String>("packageName")
                } catch (_: Throwable) {
                    null
                }
                val disable = prefs.get(DataConst.DISABLE_SPLASH_SCREEN)
                log.i("[Android] showStartingWindow(): ${if (disable) "" else "Not "}disable $pkgName splash screen")
                if (disable) null else chain.proceed()
            })

        // 热启动时生成启动遮罩
        kit.hookMethod(activityRecord, "getStartingWindowType", id = "startingType",
            hooker = XposedInterface.Hooker { chain ->
                val args = chain.args
                // 第二个布尔参表示是否为热启动 (签名变化时按位置兜底)
                val isHotStart = args.filterIsInstance<Boolean>().getOrNull(0) ?: false
                val compat = prefs.get(DataConst.ENABLE_HOT_START_COMPATIBLE) && isHotStart
                log.i("[Android] getStartingWindowType(): ${if (compat) "" else "Not "}set result to 2")
                if (compat) 2 else chain.proceed()
            })
    }
}
