package com.gswxxn.restoresplashscreen.hook

import com.gswxxn.restoresplashscreen.utils.HookPrefs
import com.gswxxn.restoresplashscreen.utils.loadClass
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Method

typealias Chain = XposedInterface.Chain

/**
 * 新管线 Hook 上下文
 *
 * 仅支持 Android 17 / HyperOS 4, 方法均按设备 ROM dump 的真实签名定位,
 * 找不到类或方法时只记日志, 绝不抛异常连累宿主
 */
class HookKit(
    val module: XposedModule,
    val classLoader: ClassLoader,
    val prefs: HookPrefs,
    val log: HLog
) {
    fun findClass(name: String): Class<*>? {
        val c = loadClass(name, classLoader)
        if (c == null) log.i("class not found: $name")
        return c
    }

    private fun hookExecutables(
        methods: List<Method>,
        ctor: Constructor<*>?,
        id: String?,
        hooker: XposedInterface.Hooker
    ): Boolean {
        val safe = XposedInterface.Hooker { chain ->
            try {
                hooker.intercept(chain)
            } catch (t: Throwable) {
                log.e("hook intercept failed", t)
                try {
                    chain.proceed()
                } catch (_: Throwable) {
                    null
                }
            }
        }
        var ok = false
        methods.forEach { m ->
            try {
                val builder = module.hook(m)
                if (id != null) builder.setId(id)
                builder.intercept(safe)
                ok = true
            } catch (t: Throwable) {
                log.e("hook failed ${m.declaringClass.name}#${m.name}", t)
            }
        }
        ctor?.let {
            try {
                val builder = module.hook(it)
                if (id != null) builder.setId(id)
                builder.intercept(safe)
                ok = true
            } catch (t: Throwable) {
                log.e("hook ctor failed", t)
            }
        }
        return ok
    }

    /** 按方法名 Hook (可限定参数个数), 未找到则记日志返回 false */
    fun hookMethod(
        className: String,
        methodName: String,
        paramCount: IntRange? = null,
        id: String? = null,
        hooker: XposedInterface.Hooker
    ): Boolean {
        val clazz = findClass(className) ?: return false
        val methods = clazz.declaredMethods.filter {
            it.name == methodName && (paramCount == null || it.parameterCount in paramCount)
        }.onEach { it.isAccessible = true }
        if (methods.isEmpty()) {
            log.i("method not found: $className#$methodName")
            return false
        }
        log.i("hooked: $className#$methodName x${methods.size}")
        return hookExecutables(methods, null, id, hooker)
    }

    /** Hook 构造方法 */
    fun hookConstructor(
        className: String,
        paramCount: IntRange? = null,
        id: String? = null,
        hooker: XposedInterface.Hooker
    ): Boolean {
        val clazz = findClass(className) ?: return false
        val ctor = clazz.declaredConstructors.firstOrNull {
            paramCount == null || it.parameterCount in paramCount
        }?.apply { isAccessible = true }
        if (ctor == null) {
            log.i("ctor not found: $className")
            return false
        }
        log.i("hooked ctor: $className")
        return hookExecutables(emptyList(), ctor, id, hooker)
    }

    /** 直接 Hook 已解析的方法 */
    fun hookDirect(
        method: Method,
        id: String? = null,
        hooker: XposedInterface.Hooker
    ): Boolean {
        method.isAccessible = true
        return hookExecutables(listOf(method), null, id, hooker)
    }

    /** before 语义: 先执行 block 再放行 */
    fun before(block: (Chain) -> Unit): XposedInterface.Hooker =
        XposedInterface.Hooker { chain ->
            block(chain)
            chain.proceed()
        }

    /** after 语义: 先放行拿到结果再处理 */
    fun after(block: (Chain, Any?) -> Any?): XposedInterface.Hooker =
        XposedInterface.Hooker { chain ->
            val result = chain.proceed()
            block(chain, result)
        }
}
