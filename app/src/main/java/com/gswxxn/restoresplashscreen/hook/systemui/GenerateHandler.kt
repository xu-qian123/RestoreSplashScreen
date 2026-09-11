package com.gswxxn.restoresplashscreen.hook.systemui

import android.os.Handler
import android.os.Looper
import com.gswxxn.restoresplashscreen.data.DataConst
import com.gswxxn.restoresplashscreen.hook.Chain
import com.gswxxn.restoresplashscreen.hook.HookKit
import com.gswxxn.restoresplashscreen.hook.Session
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/**
 * 基础设置与实验功能
 *
 * - makeSplashScreenContentView: 入口, 解析包名并处理强制开启/置空
 * - removeStartingWindow: 出口, 最小时长 + 重置会话
 *
 * HyperOS 4 真机签名:
 * - makeSplashScreenContentView(Context, StartingWindowInfo, int, Consumer)
 */
object GenerateHandler {
    fun run(kit: HookKit) {
        val prefs = kit.prefs
        val log = kit.log
        val splashType by lazy { windowType(kit, "STARTING_WINDOW_TYPE_SPLASH_SCREEN", 1) }
        val legacyType by lazy { windowType(kit, "STARTING_WINDOW_TYPE_LEGACY_SPLASH_SCREEN", 4) }

        kit.hookMethod(
            "com.android.wm.shell.startingsurface.SplashscreenContentDrawer",
            "makeSplashScreenContentView",
            id = "makeSplash",
            hooker = XposedInterface.Hooker { chain: Chain ->
                val args = chain.args
                val (pkg, activity) = pkgFromStartingInfo(args.getOrNull(1))
                Session.hooking = true
                Session.packageName = pkg
                Session.activity = activity
                Session.except = Session.isExcept(pkg, prefs)
                log.i("****** $pkg; $activity:", "makeSplashScreenContentView(): ${if (Session.except) "Except" else "Allow"} this app")

                // 强制开启启动遮罩: 改写 suggestType 参数后放行
                if (prefs.get(DataConst.FORCE_ENABLE_SPLASH_SCREEN) && !Session.except) {
                    val newArgs = args.toMutableList()
                    val intIndex = newArgs.indexOfFirst { it is Int }
                    if (intIndex != -1) {
                        newArgs[intIndex] = splashType
                        log.i("makeSplashScreenContentView(): forceEnable, set suggestType to $splashType")
                        return@Hooker chain.proceed(newArgs.toTypedArray())
                    }
                }

                // 作用域外替换为空白遮罩
                if (prefs.get(DataConst.REPLACE_TO_EMPTY_SPLASH_SCREEN) && Session.except) {
                    val newArgs = args.toMutableList()
                    val intIndex = newArgs.indexOfFirst { it is Int }
                    if (intIndex != -1) {
                        newArgs[intIndex] = legacyType
                        log.i("makeSplashScreenContentView(): replace except app with empty splash screen")
                        return@Hooker chain.proceed(newArgs.toTypedArray())
                    }
                }
                chain.proceed()
            }
        )

        // 遮罩最小持续时间, 兼作会话出口
        val removeHook = XposedInterface.Hooker { chain: Chain ->
            try {
                if (Session.except || !Session.hooking || Session.packageName.isBlank()) {
                    return@Hooker chain.proceed()
                }
                val pkg = Session.packageName
                val duration = if (pkg in prefs.get(DataConst.MIN_DURATION_LIST)) {
                    try {
                        prefs.getMap(DataConst.MIN_DURATION_CONFIG_MAP)[pkg].toString().toLong()
                    } catch (_: NumberFormatException) {
                        log.i("removeStartingWindow(): $pkg: bad MIN_DURATION config")
                        0L
                    }
                } else {
                    prefs.get(DataConst.MIN_DURATION).toLong()
                }
                if (duration == 0L) {
                    chain.proceed()
                } else {
                    // Chain 不可跨线程复用, 延迟后用反射重新调用原方法;
                    // 重入时会话已重置, 会直接放行执行原逻辑
                    log.i("removeStartingWindow(): remove $pkg splash screen after $duration ms")
                    val executable = chain.executable as? Method
                    val thisObj = chain.thisObject
                    val callArgs = chain.args.toTypedArray()
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            executable?.invoke(thisObj, *callArgs)
                        } catch (t: Throwable) {
                            log.e("removeStartingWindow(): delayed invoke failed", t)
                        }
                    }, duration)
                    null
                }
            } finally {
                Session.reset()
            }
        }
        // WMShell 与 SystemUI 两处实现, 命中哪个用哪个
        kit.hookMethod(
            "com.android.wm.shell.ShellTaskOrganizer", "removeStartingWindow",
            id = "removeStarting", hooker = removeHook
        )
        kit.hookMethod(
            "com.android.wm.shell.startingsurface.StartingWindowController", "removeStartingWindow",
            id = "removeStarting2", hooker = removeHook
        )
    }
}
