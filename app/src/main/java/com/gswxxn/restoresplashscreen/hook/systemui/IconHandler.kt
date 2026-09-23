package com.gswxxn.restoresplashscreen.hook.systemui

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import cn.fkj233.ui.activity.dp2px
import com.gswxxn.restoresplashscreen.data.DataConst
import com.gswxxn.restoresplashscreen.hook.Chain
import com.gswxxn.restoresplashscreen.hook.HookKit
import com.gswxxn.restoresplashscreen.hook.IconState
import com.gswxxn.restoresplashscreen.hook.Session
import com.gswxxn.restoresplashscreen.utils.GraphicUtils
import com.gswxxn.restoresplashscreen.utils.IconPackManager
import com.gswxxn.restoresplashscreen.utils.MIUIIconsHelper
import com.gswxxn.restoresplashscreen.utils.call
import com.gswxxn.restoresplashscreen.utils.fld
import com.gswxxn.restoresplashscreen.utils.fldAs
import com.gswxxn.restoresplashscreen.utils.fldSet
import com.gswxxn.restoresplashscreen.utils.splashIconSize
import com.gswxxn.restoresplashscreen.utils.systemApp
import com.gswxxn.restoresplashscreen.wrapper.TransparentAdaptiveIconDrawable
import io.github.libxposed.api.XposedInterface

/**
 * 图标处理
 *
 * HyperOS 4 真机签名 (Miui-WindowManager-Shell):
 * - HighResIconProvider.getIcon(ActivityInfo, int, int)
 * - com.android.miui.launcher3x.icons.IconProvider.getIcon(ComponentInfo[, int])
 * - SplashViewBuilder.createIconDrawable(Drawable, boolean, boolean)
 * - SplashViewBuilder.build() -> SplashScreenView
 */
object IconHandler {
    private const val BUILDER = "com.android.wm.shell.startingsurface.SplashscreenContentDrawer\$SplashViewBuilder"
    private const val HIGH_RES = "com.android.wm.shell.startingsurface.SplashscreenContentDrawer\$HighResIconProvider"
    private const val MIUI_PROVIDER = "com.android.miui.launcher3x.icons.IconProvider"
    private const val MIUI_FACTORY = "com.android.miui.launcher3x.icons.BaseIconFactory"
    private const val ICON_COLOR = "com.android.wm.shell.startingsurface.SplashscreenContentDrawer\$ColorCache\$IconColor"

    private var iconPack: IconPackManager? = null
    private var iconPackName: String? = null
    private var miuiIcons: MIUIIconsHelper? = null

    private fun host(): Context? = systemApp()

    private fun packManager(kit: HookKit, host: Context): IconPackManager? {
        val name = kit.prefs.get(DataConst.ICON_PACK_PACKAGE_NAME)
        if (name == "None") return null
        if (iconPack == null || iconPackName != name) {
            iconPackName = name
            iconPack = try {
                IconPackManager(host, name)
            } catch (t: Throwable) {
                kit.log.e("IconPackManager init failed", t)
                null
            }
        }
        return iconPack
    }

    private fun miuiHelper(kit: HookKit, host: Context): MIUIIconsHelper? {
        if (miuiIcons == null) {
            miuiIcons = try {
                MIUIIconsHelper(kit, host)
            } catch (t: Throwable) {
                kit.log.e("MIUIIconsHelper init failed", t)
                null
            }
        }
        return miuiIcons
    }

    private fun inSplashPath(): Boolean =
        Thread.currentThread().stackTrace.any { it.methodName == "makeSplashScreenContentView" }

    fun run(kit: HookKit) {
        val prefs = kit.prefs
        val log = kit.log

        // 忽略应用主动设置的图标
        kit.hookMethod(
            "com.android.wm.shell.startingsurface.SplashscreenContentDrawer",
            "getWindowAttrs", id = "windowAttrs",
            hooker = kit.after { chain: Chain, result: Any? ->
                if (!Session.hooking || Session.except) return@after result
                val pkg = Session.packageName
                val isDefaultStyle = prefs.get(DataConst.ENABLE_DEFAULT_STYLE) &&
                    if (prefs.get(DataConst.IS_DEFAULT_STYLE_LIST_EXCEPTION_MODE))
                        pkg !in prefs.get(DataConst.DEFAULT_STYLE_LIST)
                    else
                        pkg in prefs.get(DataConst.DEFAULT_STYLE_LIST)
                if (isDefaultStyle) {
                    (chain.args.getOrNull(1))?.fldSet("mSplashScreenIcon", null)
                }
                log.i("getWindowAttrs(): ${if (isDefaultStyle) "" else "Not "}ignore set icon")
                result
            }
        )

        // 图标主入口 (HyperOS WMShell 高分辨率 Provider)
        // 第一个参数即 ActivityInfo, 顺手补齐会话中的 Activity (入口处可能为空)
        val iconHook = kit.after { chain: Chain, result: Any? ->
            if (!Session.hooking || Session.except) return@after result
            if (Session.activity.isBlank()) {
                try {
                    val ai = chain.args.firstOrNull()
                    Session.activity = (ai?.fld("targetActivity") as? String) ?: ""
                    if (Session.activity.isNotBlank()) log.i("getIcon(): activity resolved as ${Session.activity}")
                } catch (_: Throwable) {
                }
            }
            val drawable = result as? Drawable ?: return@after result
            try {
                processIconDrawable(kit, drawable)
            } catch (t: Throwable) {
                log.e("processIconDrawable failed", t)
                drawable
            }
        }
        kit.hookMethod(HIGH_RES, "getIcon", id = "highResIcon", hooker = iconHook)
        // MIUI 自身 Provider (兜底, 防止走独立分支时漏掉)
        kit.hookMethod(MIUI_PROVIDER, "getIcon", id = "miuiIcon", hooker = iconHook)

        // 放大 MIUI 大图标 / 缩小低分辨率图标
        kit.hookMethod(BUILDER, "createIconDrawable", id = "createIcon",
            hooker = kit.before { chain: Chain ->
                if (!Session.hooking || Session.except) return@before
                try {
                    val builder = chain.thisObject ?: return@before
                    when {
                        IconState.useBigMiuiIcon == true -> {
                            val size = builder.fldAs<Int>("mFinalIconSize") ?: return@before
                            builder.fldSet("mFinalIconSize", (size * 1.35).toInt())
                            log.i("createIconDrawable(): execute enlarge icon")
                        }
                        IconState.needShrink -> {
                            val size = builder.fldAs<Int>("mFinalIconSize") ?: return@before
                            builder.fldSet("mFinalIconSize", (size / 1.5).toInt())
                            log.i("createIconDrawable(): execute shrink icon")
                        }
                    }
                } catch (t: Throwable) {
                    log.e("createIconDrawable() resize failed", t)
                }
            })

        // build 之后: 模糊背景 + 圆角
        kit.hookMethod(BUILDER, "build", id = "splashBuild",
            hooker = kit.after { chain: Chain, result: Any? ->
                if (!Session.hooking || Session.except) return@after result
                addBlurBg(kit, result)
                drawRoundCorner(kit, chain, result)
                result
            })

        // 禁止 MIUI 工厂在遮罩链路中收缩图标
        kit.hookMethod(MIUI_FACTORY, "createIconBitmap", id = "miuiBitmap",
            hooker = XposedInterface.Hooker { chain: Chain ->
                if (Session.hooking && !Session.except && inSplashPath()) {
                    val drawable = chain.args.firstOrNull { it is Drawable } as? Drawable
                    if (drawable != null) {
                        log.i("MIUI createIconBitmap(): avoid shrink icon by system ui")
                        return@Hooker GraphicUtils.drawable2Bitmap(drawable, splashIconSize(host()))
                    }
                }
                chain.proceed()
            })
        kit.hookMethod(MIUI_FACTORY, "wrapToAdaptiveIcon", id = "miuiWrap",
            hooker = XposedInterface.Hooker { chain: Chain ->
                if (Session.hooking && !Session.except && inSplashPath()) {
                    val drawable = chain.args.firstOrNull { it is Drawable }
                    if (drawable != null) {
                        log.i("MIUI wrapToAdaptiveIcon(): keep original drawable")
                        return@Hooker TransparentAdaptiveIconDrawable(drawable as Drawable)
                    }
                }
                chain.proceed()
            })

        // 强制图标背景被判定为复杂, 防止系统抹掉简单背景
        kit.hookConstructor(ICON_COLOR, id = "iconColor",
            hooker = kit.after { chain: Chain, result: Any? ->
                try {
                    chain.thisObject?.fldSet("mIsBgComplex", true)
                } catch (t: Throwable) {
                    log.e("IconColor hook failed", t)
                }
                result
            })
    }

    /**
     * 处理图标: 隐藏图标 > MIUI 大图标 > 图标包 > 替换获取方式 > 原始图标
     */
    fun processIconDrawable(kit: HookKit, oriDrawable: Drawable): Drawable {
        val prefs = kit.prefs
        val log = kit.log
        val pkg = Session.packageName
        val activity = Session.activity
        val host = host()
        val shrinkIconType = prefs.get(DataConst.SHRINK_ICON)
        val colorMode = prefs.get(DataConst.BG_COLOR_MODE)
        val isDarkMode = host?.resources?.configuration?.let {
            it.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        } ?: false

        val isHide = prefs.get(DataConst.ENABLE_HIDE_SPLASH_SCREEN_ICON) &&
            if (prefs.get(DataConst.IS_HIDE_SPLASH_SCREEN_ICON_EXCEPTION_MODE))
                pkg !in prefs.get(DataConst.HIDE_SPLASH_SCREEN_ICON_LIST)
            else
                pkg in prefs.get(DataConst.HIDE_SPLASH_SCREEN_ICON_LIST)
        if (isHide) {
            log.i("getIcon(): draw TRANSPARENT icon")
            return ColorDrawable(Color.TRANSPARENT)
        }

        val iconSize = splashIconSize(host)
        val iconDrawable = if (host != null) {
            getMIUILargeIcon(kit, host, pkg)
                ?: getIconFromPack(kit, host, pkg, activity)
                ?: replaceWayOfGetIcons(kit, host, pkg, activity)
                ?: oriDrawable
        } else oriDrawable

        val bitmap = GraphicUtils.drawable2Bitmap(
            iconDrawable,
            if (IconState.useBigMiuiIcon == true) iconSize * 2 else iconSize
        )
        IconState.needShrink = when (shrinkIconType) {
            0 -> false
            1 -> if (iconDrawable !is AdaptiveIconDrawable) iconDrawable.intrinsicWidth < iconSize / 1.5 else false
            else -> true
        }
        log.i("getIcon(): currentIsNeedShrinkIcon: ${IconState.needShrink}")

        IconState.dominantColor = GraphicUtils.getBgColor(
            bitmap,
            when (colorMode) {
                1 -> false
                2 -> !isDarkMode
                else -> true
            }
        )
        IconState.iconDrawable = iconDrawable
        return iconDrawable
    }

    private fun getMIUILargeIcon(kit: HookKit, host: Context, pkg: String): Drawable? {
        if (!prefsOf(kit).get(DataConst.ENABLE_USE_MIUI_LARGE_ICON)) return null
        val helper = miuiHelper(kit, host) ?: return null
        return try {
            if (!helper.hasLargeIcon(pkg)) return null
            kit.log.i("getIcon(): use MIUI Large Icon")
            helper.getLargeIconDrawable(pkg)?.let {
                val size = helper.getLargeIconSize(pkg)
                kit.log.i("getIcon(): large icon size: $size")
                IconState.useBigMiuiIcon = if (size in arrayOf("1x1", "1x2", "2x1", "2x2")) size != "1x1" else null
                if (size in arrayOf("1x2", "2x1")) GraphicUtils.convertToSquareDrawable(it, host.resources) else it
            }
        } catch (t: Throwable) {
            kit.log.e("getMIUILargeIcon failed", t)
            null
        }
    }

    private fun getIconFromPack(kit: HookKit, host: Context, pkg: String, activity: String): Drawable? {
        if (kit.prefs.get(DataConst.ICON_PACK_PACKAGE_NAME) == "None") return null
        val manager = packManager(kit, host) ?: return null
        return try {
            kit.log.i("getIcon(): use Icon Pack")
            when {
                // 通讯录与拨号在桌面是不同图标, 只按实际启动组件查包;
                // 查不到就返回 null 走后续分支, 禁止回退到包主入口 (拨号) 图标
                pkg == "com.android.contacts" ->
                    manager.getIconByComponentName("ComponentInfo{$pkg/$activity}")
                        ?: manager.getIconByComponentDrawableName("ComponentInfo{$pkg/$activity}")
                else -> manager.getIconByPackageName(pkg)
            }
        } catch (t: Throwable) {
            kit.log.e("getIconFromPack failed", t)
            null
        }
    }

    private fun replaceWayOfGetIcons(kit: HookKit, host: Context, pkg: String, activity: String): Drawable? {
        if (!kit.prefs.get(DataConst.ENABLE_REPLACE_ICON) && pkg != "com.android.settings") return null
        return try {
            kit.log.i("getIcon(): replace way of getting icon")
            when {
                // 通讯录与拨号在桌面是不同图标, 按实际启动的 Activity 取图标, 失败回退到包图标
                pkg == "com.android.contacts" && activity.isNotBlank() -> try {
                    host.packageManager.getActivityIcon(ComponentName(pkg, activity))
                } catch (_: Throwable) {
                    host.packageManager.getApplicationIcon(pkg)
                }
                pkg == "com.android.settings" && activity == "com.android.settings.BackgroundApplicationsManager" ->
                    host.packageManager.getApplicationIcon("com.android.settings")
                else -> host.packageManager.getApplicationIcon(pkg)
            }
        } catch (t: Throwable) {
            kit.log.e("replaceWayOfGetIcons failed for $pkg", t)
            null
        }
    }

    private fun prefsOf(kit: HookKit) = kit.prefs

    private fun addBlurBg(kit: HookKit, splashView: Any?) {
        val log = kit.log
        if (kit.prefs.get(DataConst.SHRINK_ICON) == 0 || !kit.prefs.get(DataConst.ENABLE_ADD_ICON_BLUR_BG)) return
        if (!IconState.needShrink || IconState.useBigMiuiIcon == true) return
        try {
            val host = host() ?: return
            val view = splashView as? FrameLayout ?: return
            val iconView = view.fld("mIconView") as? ImageView ?: return
            val iconSize = (splashIconSize(host) / 1.5).toInt()
            val bgIconSize = iconSize * 4
            val blurBg = GraphicUtils.createShadowedIcon(
                host, IconState.iconDrawable, iconSize, iconSize * 4,
                iconSize * kit.prefs.getDev(DataConst.DEV_ICON_ROUND_CORNER_RATE) / 100f
            ) ?: return
            val bgView = ImageView(host).apply {
                setImageDrawable(blurBg)
                setRenderEffect(
                    RenderEffect.createBlurEffect(
                        bgIconSize.toFloat() / 10, bgIconSize.toFloat() / 10, Shader.TileMode.DECAL
                    )
                )
                z = -1f
            }
            view.addView(bgView, FrameLayout.LayoutParams(bgIconSize, bgIconSize).apply { gravity = Gravity.CENTER })
            iconView.alpha = 0.9f
            log.i("build(): add icon blur bg")
        } catch (t: Throwable) {
            log.e("addBlurBg failed", t)
        }
    }

    private fun drawRoundCorner(kit: HookKit, chain: Chain, splashView: Any?) {
        val log = kit.log
        try {
            val host = host() ?: return
            val view = splashView as? FrameLayout ?: return
            val iconView = view.fld("mIconView") as? View ?: return
            val builder = chain.thisObject ?: return
            val iconSize = builder.fldAs<Int>("mIconSize") ?: 0
            val iconDrawable = builder.fld("mIconDrawable") as? Drawable ?: return
            val needCorner = kit.prefs.get(DataConst.ENABLE_DRAW_ROUND_CORNER) &&
                "android.window.SplashScreenView\$IconAnimateListener" !in iconDrawable.javaClass.interfaces.map { it.name } &&
                iconSize != 0 &&
                IconState.useBigMiuiIcon == null
            if (!needCorner) return
            val rate = kit.prefs.getDev(DataConst.DEV_ICON_ROUND_CORNER_RATE)
            iconView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val border = dp2px(host, 1.5f)
                    outline.setRoundRect(
                        border, border, view.width - border, view.height - border,
                        iconSize.toFloat() * rate / 100
                    )
                }
            }
            iconView.clipToOutline = true
            log.i("build(): draw icon round corner")
        } catch (t: Throwable) {
            log.e("drawRoundCorner failed", t)
        }
    }
}
