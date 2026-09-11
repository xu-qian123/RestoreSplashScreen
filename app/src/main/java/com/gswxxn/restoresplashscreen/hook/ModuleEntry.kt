package com.gswxxn.restoresplashscreen.hook

import com.gswxxn.restoresplashscreen.utils.HookPrefs
import com.gswxxn.restoresplashscreen.utils.Prefs
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 模块入口 (Modern Xposed API 102)
 *
 * 仅支持 Android 17 / HyperOS 4:
 * - android: system_server, 启动遮罩开关类控制
 * - com.android.systemui: WMShell 启动遮罩绘制 (实现位于 Miui-WindowManager-Shell)
 */
class ModuleEntry : XposedModule() {
    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        val prefs = try {
            HookPrefs(getRemotePreferences(Prefs.GROUP))
        } catch (_: Throwable) {
            HookPrefs(null)
        }
        val log = HLog(this, prefs)
        val kit = HookKit(this, param.defaultClassLoader, prefs, log)
        when (param.packageName) {
            "android" -> {
                log.i("module loaded in android")
                SystemServerHooks(kit).run()
            }
            "com.android.systemui" -> {
                log.i("module loaded in com.android.systemui")
                SystemUIHooks(kit).run()
            }
        }
    }
}
