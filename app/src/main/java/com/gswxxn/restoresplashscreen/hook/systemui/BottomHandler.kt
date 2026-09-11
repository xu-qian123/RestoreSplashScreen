package com.gswxxn.restoresplashscreen.hook.systemui

import com.gswxxn.restoresplashscreen.data.DataConst
import com.gswxxn.restoresplashscreen.hook.Chain
import com.gswxxn.restoresplashscreen.hook.HookKit
import com.gswxxn.restoresplashscreen.hook.Session
import com.gswxxn.restoresplashscreen.utils.fld
import com.gswxxn.restoresplashscreen.utils.fldSet

/** 移除底部品牌图 */
object BottomHandler {
    private const val BUILDER = "com.android.wm.shell.startingsurface.SplashscreenContentDrawer\$SplashViewBuilder"

    fun run(kit: HookKit) {
        val prefs = kit.prefs
        val log = kit.log
        kit.hookMethod(BUILDER, "build", id = "bottomBuild",
            hooker = kit.before { chain: Chain ->
                if (!Session.hooking || Session.except) return@before
                val pkg = Session.packageName
                val remove = prefs.get(DataConst.REMOVE_BRANDING_IMAGE) &&
                    if (prefs.get(DataConst.IS_REMOVE_BRANDING_IMAGE_EXCEPTION_MODE))
                        pkg !in prefs.get(DataConst.REMOVE_BRANDING_IMAGE_LIST)
                    else
                        pkg in prefs.get(DataConst.REMOVE_BRANDING_IMAGE_LIST)
                if (remove) {
                    try {
                        chain.thisObject?.fld("this\$0")?.fld("mTmpAttrs")?.fldSet("mBrandingImage", null)
                    } catch (t: Throwable) {
                        log.e("build(): remove Branding Image failed", t)
                    }
                }
                log.i("build(): ${if (remove) "" else "Not "}remove Branding Image")
            })
    }
}
