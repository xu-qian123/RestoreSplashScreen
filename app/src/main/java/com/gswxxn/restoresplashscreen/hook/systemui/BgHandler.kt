package com.gswxxn.restoresplashscreen.hook.systemui

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.gswxxn.restoresplashscreen.data.DataConst
import com.gswxxn.restoresplashscreen.hook.BgState
import com.gswxxn.restoresplashscreen.hook.Chain
import com.gswxxn.restoresplashscreen.hook.HookKit
import com.gswxxn.restoresplashscreen.hook.IconState
import com.gswxxn.restoresplashscreen.hook.Session
import com.gswxxn.restoresplashscreen.utils.GraphicUtils
import com.gswxxn.restoresplashscreen.utils.call
import com.gswxxn.restoresplashscreen.utils.fld
import com.gswxxn.restoresplashscreen.utils.fldAs
import com.gswxxn.restoresplashscreen.utils.fldSet
import com.gswxxn.restoresplashscreen.utils.systemApp

/**
 * 背景处理
 *
 * HyperOS 4 上直接在 SplashViewBuilder.build() 之前调用 setWindowBGColor(),
 * 不再经过 framework 的 SplashScreenView.Builder
 */
object BgHandler {
    private const val BUILDER = "com.android.wm.shell.startingsurface.SplashscreenContentDrawer\$SplashViewBuilder"
    private const val DRAWER = "com.android.wm.shell.startingsurface.SplashscreenContentDrawer"

    fun run(kit: HookKit) {
        val log = kit.log
        kit.hookMethod(BUILDER, "build", id = "bgBuild",
            hooker = kit.before { chain: Chain ->
                if (!Session.hooking || Session.except) return@before
                try {
                    val builder = chain.thisObject ?: return@before
                    getColor(kit)?.let { color ->
                        builder.fldSet("mThemeColor", color)
                        builder.call("setWindowBGColor", color)
                        log.i("build(): set background color")
                    }
                } catch (t: Throwable) {
                    log.e("build(): set background color failed", t)
                }
            })
    }

    private fun getColor(kit: HookKit): Int? {
        val prefs = kit.prefs
        val log = kit.log
        val pkg = Session.packageName
        val host = systemApp()
        val isDarkMode = host?.resources?.configuration?.let {
            it.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        } ?: false
        val bgColorMode = prefs.get(DataConst.BG_COLOR_MODE)
        val bgColorType = prefs.get(DataConst.CHANG_BG_COLOR_TYPE)
        val isInExcept = pkg in prefs.get(DataConst.BG_EXCEPT_LIST)
        val ignoreDarkMode = prefs.get(DataConst.IGNORE_DARK_MODE)
        val individualMap = prefs.getMap(
            if (!isDarkMode) DataConst.INDIVIDUAL_BG_COLOR_APP_MAP
            else DataConst.INDIVIDUAL_BG_COLOR_APP_MAP_DARK
        )
        val tmpAttrs = BgState.tmpAttrs
        val windowBgColor = try {
            tmpAttrs?.fldAs<Int>("mWindowBgColor") ?: 0
        } catch (_: Throwable) {
            0
        }
        if (bgColorType != 0 && pkg !in individualMap.keys &&
            prefs.get(DataConst.SKIP_APP_WITH_BG_COLOR) && windowBgColor != 0
        ) {
            log.i("build(): skip set bg color cuz app has been set bg color")
            return null
        }

        if (pkg in individualMap.keys) {
            log.i("build(): set individual background color, ${individualMap[pkg]}")
            return try {
                Color.parseColor(individualMap[pkg])
            } catch (_: Throwable) {
                null
            }
        }
        if (isInExcept || (isDarkMode && !ignoreDarkMode)) {
            log.i("build(): skip set bg color cuz app in except list")
            return null
        }
        return when (bgColorType) {
            1 -> {
                log.i("build(): get adaptive background color")
                IconState.dominantColor ?: try {
                    (tmpAttrs?.fld("mSplashScreenIcon") as? Drawable)?.let { drawable ->
                        GraphicUtils.getBgColor(
                            GraphicUtils.drawable2Bitmap(drawable, 100),
                            when (bgColorMode) {
                                1 -> false
                                2 -> !isDarkMode
                                else -> true
                            }
                        )
                    }
                } catch (_: Throwable) {
                    null
                }
            }
            2 -> {
                log.i("build(): get monet background color")
                if (host == null) return null
                try {
                    when (bgColorMode) {
                        0 -> dynamicLightColorScheme(host).primaryContainer.toArgb()
                        1 -> dynamicDarkColorScheme(host).surface.toArgb()
                        else -> if (!isDarkMode)
                            dynamicLightColorScheme(host).primaryContainer.toArgb()
                        else
                            dynamicDarkColorScheme(host).surface.toArgb()
                    }
                } catch (t: Throwable) {
                    log.e("build(): monet color failed", t)
                    null
                }
            }
            3 -> {
                log.i("build(): set overall background color")
                try {
                    Color.parseColor(prefs.get(if (isDarkMode) DataConst.OVERALL_BG_COLOR_NIGHT else DataConst.OVERALL_BG_COLOR))
                } catch (_: Throwable) {
                    null
                }
            }
            else -> {
                log.i("build(): not replace background color")
                null
            }
        }
    }
}
