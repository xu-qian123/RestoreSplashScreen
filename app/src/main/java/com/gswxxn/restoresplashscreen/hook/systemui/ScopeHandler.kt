package com.gswxxn.restoresplashscreen.hook.systemui

import android.content.Context
import com.gswxxn.restoresplashscreen.hook.BgState
import com.gswxxn.restoresplashscreen.hook.Chain
import com.gswxxn.restoresplashscreen.hook.HookKit
import com.gswxxn.restoresplashscreen.hook.Session
import com.gswxxn.restoresplashscreen.utils.call
import com.gswxxn.restoresplashscreen.utils.fld
import com.gswxxn.restoresplashscreen.utils.fldSet
import io.github.libxposed.api.XposedInterface

/**
 * 作用域相关
 *
 * - getBGColorFromCache: 将 mIconBgColor 置为固定值, 骗过 HyperOS 的额外判断
 * - SplashViewBuilder 构造后: 调 getWindowAttrs 还原被影响的 mTmpAttrs
 */
object ScopeHandler {
    private const val DRAWER = "com.android.wm.shell.startingsurface.SplashscreenContentDrawer"
    private const val BUILDER = "com.android.wm.shell.startingsurface.SplashscreenContentDrawer\$SplashViewBuilder"

    fun run(kit: HookKit) {
        val log = kit.log

        kit.hookMethod(DRAWER, "getBGColorFromCache", id = "bgColorCache",
            hooker = kit.after { chain: Chain, result: Any? ->
                if (!Session.hooking || Session.except) return@after result
                try {
                    val tmpAttrs = chain.thisObject?.fld("mTmpAttrs")
                    tmpAttrs?.fldSet("mIconBgColor", 1)
                    BgState.tmpAttrs = tmpAttrs
                    log.i("getBGColorFromCache(): set mIconBgColor to 1")
                } catch (t: Throwable) {
                    log.e("getBGColorFromCache() failed", t)
                }
                result
            })

        kit.hookConstructor(BUILDER, id = "builderCtor",
            hooker = kit.after { chain: Chain, result: Any? ->
                if (!Session.hooking || Session.except) return@after result
                try {
                    val drawer = chain.thisObject?.fld("this\$0")
                    val tmpAttrs = drawer?.fld("mTmpAttrs")
                    val context = chain.args.firstOrNull { it is Context }
                    if (drawer != null && tmpAttrs != null && context != null) {
                        drawer.call("getWindowAttrs", context, tmpAttrs)
                    }
                } catch (t: Throwable) {
                    log.e("builderCtor reset mTmpAttrs failed", t)
                }
                result
            })
    }
}
