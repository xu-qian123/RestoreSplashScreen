package com.gswxxn.restoresplashscreen.hook.systemui

import com.gswxxn.restoresplashscreen.data.DataConst
import com.gswxxn.restoresplashscreen.hook.Chain
import com.gswxxn.restoresplashscreen.hook.HookKit
import com.gswxxn.restoresplashscreen.hook.Session
import io.github.libxposed.api.XposedInterface

/**
 * HyperOS 专属
 *
 * - isMiuiHome: 移除截图背景, 欺骗 fillViewWithIcon 中的启动器判断
 */
object MiuiHandler {
    fun run(kit: HookKit) {
        val prefs = kit.prefs
        val log = kit.log
        kit.hookMethod("android.app.TaskSnapshotHelperImpl", "isMiuiHome", id = "miuiHome",
            hooker = XposedInterface.Hooker { chain: Chain ->
                if (!Session.hooking || Session.except) return@Hooker chain.proceed()
                if (prefs.get(DataConst.REMOVE_BG_DRAWABLE)) {
                    log.i("isMiuiHome(): set isMiuiHome() false")
                    false
                } else chain.proceed()
            })
    }
}
