package com.gswxxn.restoresplashscreen.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.UserHandle
import android.provider.Settings
import com.gswxxn.restoresplashscreen.hook.Chain
import com.gswxxn.restoresplashscreen.hook.HookKit
import com.gswxxn.restoresplashscreen.utils.GraphicUtils.getCenterDrawable
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/**
 * 从 MIUI 桌面检索大图标 / 完美图标 (仅 HyperOS)
 */
@SuppressLint("DiscouragedApi")
class MIUIIconsHelper(private val kit: HookKit, private val context: Context) {
    private val miuiHomeContext = try {
        context.createPackageContext(
            "com.miui.home",
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
        )
    } catch (t: Throwable) {
        kit.log.e("create miui.home context failed", t)
        null
    }
    private val homeCl: ClassLoader? = miuiHomeContext?.classLoader

    private fun homeClass(name: String): Class<*>? = homeCl?.let { loadClass(name, it) }

    /** 当前是否启用 MIUI 完美图标 */
    val isSupportMIUIModeIcon by lazy {
        try {
            Settings.System.getInt(context.contentResolver, "key_miui_mod_icon_enable", 0) == 1
        } catch (_: Throwable) {
            false
        }
    }

    init {
        // 防止获取到 System UI 的 Resources
        homeClass("miuix.pickerwidget.date.CalendarFormatSymbols")?.let { clazz ->
            findMethod(clazz, "getWeekDays")?.let { method ->
                hookQuiet(method, "miuiWeekDays") { chain ->
                    val resources = miuiHomeContext?.resources
                    val id = resources?.getIdentifier("week_days", "array", "com.miui.home") ?: 0
                    if (id != 0) resources?.getStringArray(id) else chain.proceed()
                }
            }
        }

        // 为完美图标设置缓存时间, 避免天气等费时图标显示问题
        homeClass("com.miui.maml.util.AppIconsHelper")?.let { clazz ->
            findMethod(clazz, "getFancyIconDrawable")?.let { method ->
                hookQuiet(method, "fancyCache") { chain ->
                    try {
                        val args = chain.args.toMutableList()
                        val strIndex = args.indexOfFirst { it is String }
                        val longIndex = args.indexOfFirst { it is Long }
                        if (strIndex != -1 && longIndex != -1) {
                            args[longIndex] = getCacheTime(args[strIndex] as String)
                            return@hookQuiet chain.proceed(args.toTypedArray())
                        }
                    } catch (_: Throwable) {
                    }
                    chain.proceed()
                }
            }
        }

        // 只获取本地天气数据
        homeClass("com.miui.maml.data.ContentProviderBinder")?.let { clazz ->
            findMethod(clazz, "getUriText")?.let { method ->
                hookQuiet(method, "weatherLocal") { chain ->
                    val result = chain.proceed() as? String
                    if (result == "content://weather/actualWeatherData/1")
                        "content://weather/actualWeatherData/2"
                    else result
                }
            }
        }

        // 大图标配置每次重读
        homeClass("com.miui.maml.util.LargeIconsHelper")?.let { clazz ->
            findMethod(clazz, "hasLargeIcon")?.let { method ->
                hookQuiet(method, "largeReset") { chain ->
                    try {
                        val helperClazz = homeClass("com.miui.maml.util.LargeIconsHelper")
                        val field = helperClazz?.declaredFields?.firstOrNull { it.name == "sManagerList" }
                        field?.apply { isAccessible = true }?.set(null, null)
                    } catch (_: Throwable) {
                    }
                    chain.proceed()
                }
            }
        }
    }

    private fun hookQuiet(method: Method, id: String, hooker: (Chain) -> Any?): Boolean {
        return try {
            method.isAccessible = true
            val builder = kit.module.hook(method)
            builder.setId(id)
            builder.intercept(XposedInterface.Hooker { chain ->
                try {
                    hooker(chain)
                } catch (t: Throwable) {
                    kit.log.e("MIUIIcons hook $id failed", t)
                    try {
                        chain.proceed()
                    } catch (_: Throwable) {
                        null
                    }
                }
            })
            true
        } catch (t: Throwable) {
            kit.log.e("MIUIIcons hook $id register failed", t)
            false
        }
    }

    private fun currentUser(): Any? = try {
        UserHandle::class.java.getDeclaredField("CURRENT").apply { isAccessible = true }.get(null)
    } catch (_: Throwable) {
        null
    }

    fun hasLargeIcon(packageName: String) = try {
        val clazz = homeClass("com.miui.maml.util.LargeIconsHelper") ?: return false
        val method = clazz.declaredMethods.firstOrNull {
            it.name == "hasLargeIcon" && it.parameterCount == 4
        }?.apply { isAccessible = true } ?: return false
        (method.invoke(null, packageName, null, "desktop", currentUser()) as? Boolean) ?: false
    } catch (t: Throwable) {
        kit.log.e("hasLargeIcon failed for $packageName", t)
        false
    }

    fun getLargeIconSize(packageName: String): String? = try {
        val clazz = homeClass("com.miui.maml.util.LargeIconsHelper") ?: return null
        val configFile = clazz.declaredMethods.firstOrNull {
            it.name == "getLargeIconConfigFile" && it.parameterCount == 2
        }?.apply { isAccessible = true }?.invoke(null, "desktop", false) ?: return null
        val configs = findMethod(configFile.javaClass, "getIconsConfigs")?.invoke(configFile)
            as? HashMap<*, *> ?: return null
        val entry = configs[packageName] ?: return null
        entry.fldAs<String>("size")
    } catch (t: Throwable) {
        kit.log.e("getLargeIconSize failed for $packageName", t)
        null
    }

    fun getLargeIconDrawable(packageName: String): Drawable? = try {
        val home = miuiHomeContext ?: return null
        val clazz = homeClass("com.miui.maml.util.LargeIconsHelper") ?: return null
        val method = clazz.declaredMethods.firstOrNull {
            it.name == "getLargeIconDrawable" && it.parameterCount == 7
        }?.apply { isAccessible = true } ?: return null
        val result = method.invoke(
            null, home, packageName, null, "desktop", null, 0L, currentUser()
        )
        findMethod(result.javaClass, "getDrawable")?.invoke(result) as? Drawable
    } catch (t: Throwable) {
        kit.log.e("getLargeIconDrawable failed for $packageName", t)
        null
    }

    fun getFancyIconDrawable(packageName: String): Drawable? = try {
        val clazz = homeClass("com.miui.maml.util.AppIconsHelper") ?: return null
        val method = clazz.declaredMethods.firstOrNull {
            it.name == "getIconDrawable" && it.parameterCount == 4
        }?.apply { isAccessible = true } ?: return null
        val drawable = method.invoke(null, context, packageName, null, getCacheTime(packageName))

        if (drawable is AdaptiveIconDrawable && drawable.javaClass.name == "com.miui.maml.MamlAdaptiveIconDrawable") {
            val layer0 = (drawable.background?.call("getQuietDrawable")) as? Drawable ?: return null
            @Suppress("UNCHECKED_CAST")
            val layers = (drawable.call("getLayerFancyDrawables") as? ArrayList<Drawable>) ?: return null
            layers.add(0, layer0)
            getCenterDrawable(LayerDrawable(layers.toTypedArray()), 0.65f, context.resources)
        } else if (drawable?.javaClass?.name == "com.miui.maml.FancyDrawable") {
            drawable as Drawable
        } else null
    } catch (t: Throwable) {
        kit.log.e("getFancyIconDrawable failed for $packageName", t)
        null
    }

    private fun getCacheTime(packageName: String) = when (packageName) {
        "com.miui.weather2" -> 3600000L
        "com.android.deskclock" -> 0L
        else -> 86400000L
    }
}
