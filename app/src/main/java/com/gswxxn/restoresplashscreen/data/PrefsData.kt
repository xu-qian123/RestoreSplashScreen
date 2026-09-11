package com.gswxxn.restoresplashscreen.data

/**
 * 配置键定义, [value] 为默认值
 *
 * Hook 侧通过 LSPosed RemotePreferences 读取, UI 侧通过本地 SharedPreferences 读写并镜像到远端
 */
class PrefsData<T>(val key: String, val value: T)
