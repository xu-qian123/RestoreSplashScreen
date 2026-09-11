package com.gswxxn.restoresplashscreen.hook

import com.gswxxn.restoresplashscreen.hook.systemui.BgHandler
import com.gswxxn.restoresplashscreen.hook.systemui.BottomHandler
import com.gswxxn.restoresplashscreen.hook.systemui.GenerateHandler
import com.gswxxn.restoresplashscreen.hook.systemui.IconHandler
import com.gswxxn.restoresplashscreen.hook.systemui.MiuiHandler
import com.gswxxn.restoresplashscreen.hook.systemui.ScopeHandler

/**
 * com.android.systemui 进程 Hook 编排 (仅 Android 17 / HyperOS 4)
 *
 * 链路: Generate(包名入口/时长出口) -> Scope -> Icon -> Bg -> Bottom -> Miui
 */
class SystemUIHooks(private val kit: HookKit) {
    fun run() {
        GenerateHandler.run(kit)
        ScopeHandler.run(kit)
        IconHandler.run(kit)
        BgHandler.run(kit)
        BottomHandler.run(kit)
        MiuiHandler.run(kit)
    }
}
