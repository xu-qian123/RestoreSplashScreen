package com.gswxxn.restoresplashscreen.utils

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/** 按名称在类层级中查找字段 */
private fun findField(clazz: Class<*>, name: String): Field? {
    var c: Class<*>? = clazz
    while (c != null && c != Any::class.java) {
        try {
            return c.getDeclaredField(name).apply { isAccessible = true }
        } catch (_: NoSuchFieldException) {
            c = c.superclass
        }
    }
    return null
}

/** 使用宿主 ClassLoader 加载类, 失败返回 null */
fun loadClass(name: String, classLoader: ClassLoader? = null): Class<*>? = try {
    if (classLoader != null) Class.forName(name, false, classLoader) else Class.forName(name)
} catch (_: Throwable) {
    null
}

/** 取实例字段 */
fun Any.fld(name: String): Any? = try {
    findField(javaClass, name)?.get(this)
} catch (_: Throwable) {
    null
}

/** 取实例字段并转换类型 */
@Suppress("UNCHECKED_CAST")
fun <T> Any.fldAs(name: String): T? = try {
    fld(name) as T?
} catch (_: Throwable) {
    null
}

/** 设置实例字段 */
fun Any.fldSet(name: String, value: Any?): Boolean = try {
    findField(javaClass, name)?.set(this, value)
    true
} catch (_: Throwable) {
    false
}

/** 按名称+参数个数查找方法 */
fun findMethod(clazz: Class<*>, name: String, paramCount: IntRange = 0..30): Method? {
    var c: Class<*>? = clazz
    while (c != null && c != Any::class.java) {
        c.declaredMethods.firstOrNull { it.name == name && it.parameterCount in paramCount }?.let {
            return it.apply { isAccessible = true }
        }
        c = c.superclass
    }
    return null
}

/** 调用实例方法 (按名称+参数个数匹配) */
fun Any.call(name: String, vararg args: Any?, paramCount: IntRange = 0..30): Any? = try {
    findMethod(javaClass, name, paramCount)?.invoke(this, *args)
} catch (_: Throwable) {
    null
}

/** 调用静态方法 */
fun Class<*>.callStatic(name: String, vararg args: Any?, paramCount: IntRange = 0..30): Any? = try {
    findMethod(this, name, paramCount)?.invoke(null, *args)
} catch (_: Throwable) {
    null
}

/** 查找构造方法 */
fun findConstructor(clazz: Class<*>, paramCount: IntRange = 0..30): Constructor<*>? =
    clazz.declaredConstructors.firstOrNull { it.parameterCount in paramCount }?.apply { isAccessible = true }

/** 按类型查找实例字段 (用于 UI 侧视图查找) */
fun <T> Any.fieldByType(type: Class<T>): T? {
    var c: Class<*>? = javaClass
    while (c != null && c != Any::class.java) {
        c.declaredFields.firstOrNull { it.type == type }?.let {
            return try {
                it.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                it.get(this) as T?
            } catch (_: Throwable) {
                null
            }
        }
        c = c.superclass
    }
    return null
}

/** 获取宿主进程 Application, 失败返回 null */
fun systemApp(): android.content.Context? = try {
    val activityThread = Class.forName("android.app.ActivityThread")
    val currentApp = activityThread.getDeclaredMethod("currentApplication").apply { isAccessible = true }.invoke(null)
    currentApp as? android.content.Context
} catch (_: Throwable) {
    null
}

/** SplashScreen 图标基准尺寸 (systemui 进程内解析失败则返回默认值) */
fun splashIconSize(context: android.content.Context?): Int {
    if (context == null) return 192
    return try {
        val id = context.resources.getIdentifier("starting_surface_icon_size", "dimen", "android")
        if (id != 0) context.resources.getDimensionPixelSize(id) else 192
    } catch (_: Throwable) {
        192
    }
}
