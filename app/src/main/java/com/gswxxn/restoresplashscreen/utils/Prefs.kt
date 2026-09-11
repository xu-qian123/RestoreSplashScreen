package com.gswxxn.restoresplashscreen.utils

import android.content.Context
import android.content.SharedPreferences
import com.gswxxn.restoresplashscreen.data.DataConst
import com.gswxxn.restoresplashscreen.data.PrefsData
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * 配置存储
 *
 * UI 侧读写本地 SharedPreferences 并镜像到 LSPosed 远端 (service 绑定后),
 * Hook 侧通过 [XposedModule.getRemotePreferences] 只读远端
 */
object Prefs {
    const val GROUP = "config"
    private const val LOCAL_FILE = "restore_splash_prefs"

    @Volatile var service: XposedService? = null
        private set
    val isModuleActive: Boolean get() = service != null
    var frameworkName: String = ""
    var frameworkVersion: String = ""
    var apiVersion: Int = 0
    var scope: List<String> = emptyList()
    var onServiceChanged: (() -> Unit)? = null

    private var listenerRegistered = false

    /** 在 UI 进程注册一次即可 */
    fun ensureService(context: Context) {
        if (listenerRegistered) return
        listenerRegistered = true
        try {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(bound: XposedService) {
                    service = bound
                    try {
                        frameworkName = bound.frameworkName
                        frameworkVersion = bound.frameworkVersion
                        apiVersion = bound.apiVersion
                    } catch (_: Throwable) {
                    }
                    try {
                        @Suppress("UNCHECKED_CAST")
                        scope = (bound.scope as? List<String>) ?: emptyList()
                    } catch (_: Throwable) {
                    }
                    pushAll(context.applicationContext ?: context)
                    try {
                        onServiceChanged?.invoke()
                    } catch (_: Throwable) {
                    }
                }

                override fun onServiceDied(dead: XposedService) {
                    if (service === dead) service = null
                    try {
                        onServiceChanged?.invoke()
                    } catch (_: Throwable) {
                    }
                }
            })
        } catch (_: Throwable) {
        }
    }

    internal fun remotePrefs(): SharedPreferences? = try {
        service?.getRemotePreferences(GROUP)
    } catch (_: Throwable) {
        null
    }

    /** service 绑定后全量推送一次本地配置 */
    internal fun pushAll(context: Context) {
        try {
            val local = local(context).all ?: return
            val e = remotePrefs()?.edit() ?: return
            local.forEach { (k, v) ->
                when (v) {
                    is Boolean -> e.putBoolean(k, v)
                    is Int -> e.putInt(k, v)
                    is Long -> e.putLong(k, v)
                    is String -> e.putString(k, v)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") e.putStringSet(k, v as Set<String>)
                }
            }
            e.apply()
        } catch (_: Throwable) {
        }
    }

    internal fun local(context: Context): SharedPreferences =
        context.getSharedPreferences(LOCAL_FILE, Context.MODE_PRIVATE)
}

/** UI 侧配置桥 */
fun Context.prefs(): PrefsBridge = PrefsBridge(this)

class PrefsBridge(private val context: Context) {
    private val sp: SharedPreferences get() = Prefs.local(context)

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: PrefsData<T>): T {
        val def = key.value
        return try {
            when (def) {
                is Boolean -> sp.getBoolean(key.key, def) as T
                is Int -> sp.getInt(key.key, def) as T
                is Long -> sp.getLong(key.key, def) as T
                is String -> sp.getString(key.key, def) as T
                is Set<*> -> @Suppress("UNCHECKED_CAST")
                (sp.getStringSet(key.key, def as Set<String>) ?: def) as T
                else -> def
            }
        } catch (_: Throwable) {
            def
        }
    }

    fun edit(): PrefsEditor = PrefsEditor(sp)

    fun edit(block: PrefsEditor.() -> Unit) {
        edit().apply(block).apply()
    }

    fun all(): Map<String, *> = try {
        sp.all ?: emptyMap<String, Any>()
    } catch (_: Throwable) {
        emptyMap<String, Any>()
    }
}

class PrefsEditor(private val local: SharedPreferences) {
    private val editor: SharedPreferences.Editor = local.edit()
    private val remoteOps = mutableListOf<(SharedPreferences.Editor) -> Unit>()

    @Suppress("UNCHECKED_CAST")
    fun <T> put(key: PrefsData<T>, value: T): PrefsEditor {
        when (value) {
            is Boolean -> {
                editor.putBoolean(key.key, value)
                remoteOps += { it.putBoolean(key.key, value) }
            }
            is Int -> {
                editor.putInt(key.key, value)
                remoteOps += { it.putInt(key.key, value) }
            }
            is Long -> {
                editor.putLong(key.key, value)
                remoteOps += { it.putLong(key.key, value) }
            }
            is String -> {
                editor.putString(key.key, value)
                remoteOps += { it.putString(key.key, value) }
            }
            is Set<*> -> {
                editor.putStringSet(key.key, value as Set<String>)
                remoteOps += { it.putStringSet(key.key, value as Set<String>) }
            }
        }
        return this
    }

    fun putString(key: String, value: String?): PrefsEditor {
        editor.putString(key, value)
        remoteOps += { it.putString(key, value) }
        return this
    }

    fun putBoolean(key: String, value: Boolean): PrefsEditor {
        editor.putBoolean(key, value)
        remoteOps += { it.putBoolean(key, value) }
        return this
    }

    fun putInt(key: String, value: Int): PrefsEditor {
        editor.putInt(key, value)
        remoteOps += { it.putInt(key, value) }
        return this
    }

    fun putLong(key: String, value: Long): PrefsEditor {
        editor.putLong(key, value)
        remoteOps += { it.putLong(key, value) }
        return this
    }

    fun putStringSet(key: String, value: Set<String>?): PrefsEditor {
        editor.putStringSet(key, value)
        remoteOps += { it.putStringSet(key, value) }
        return this
    }

    fun clear(): PrefsEditor {
        editor.clear()
        remoteOps += { it.clear() }
        return this
    }

    private fun mirror() {
        try {
            val remote = Prefs.remotePrefs()?.edit() ?: return
            remoteOps.forEach { it(remote) }
            remote.apply()
        } catch (_: Throwable) {
        }
    }

    fun commit(): Boolean = try {
        editor.commit().also { mirror() }
    } catch (_: Throwable) {
        false
    }

    fun apply() {
        try {
            editor.apply()
        } catch (_: Throwable) {
        }
        mirror()
    }
}

/** Hook 侧只读配置 */
class HookPrefs(private val sp: SharedPreferences?) {
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: PrefsData<T>): T {
        val def = key.value
        if (sp == null) return def
        return try {
            when (def) {
                is Boolean -> sp.getBoolean(key.key, def) as T
                is Int -> sp.getInt(key.key, def) as T
                is Long -> sp.getLong(key.key, def) as T
                is String -> sp.getString(key.key, def) as T
                is Set<*> -> @Suppress("UNCHECKED_CAST")
                (sp.getStringSet(key.key, def as Set<String>)?.toMutableSet() ?: def) as T
                else -> def
            }
        } catch (_: Throwable) {
            def
        }
    }

    fun getMap(key: PrefsData<MutableSet<String>>): MutableMap<String, String> =
        get(key).toMap()

    fun getDev(key: PrefsData<Int>): Int =
        if (get(DataConst.ENABLE_DEV_SETTINGS)) get(key) else key.value

    private fun Set<String>.toMap(): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        forEach { item ->
            val separatorIndex = item.lastIndexOf("_")
            if (separatorIndex != -1 && separatorIndex < item.length - 1) {
                result[item.substring(0, separatorIndex)] = item.substring(separatorIndex + 1)
            }
        }
        return result
    }
}
