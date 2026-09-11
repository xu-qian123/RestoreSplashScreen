package com.gswxxn.restoresplashscreen.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.WindowCompat
import androidx.viewbinding.ViewBinding
import com.gswxxn.restoresplashscreen.utils.CommonUtils.isDarkMode
import java.lang.reflect.ParameterizedType

/**
 * 改自 [MIUINativeNotifyIcon](https://github.com/fankes/MIUINativeNotifyIcon/blob/master/app/src/main/java/com/fankes/miui/notify/ui/activity/base/BaseActivity.kt)
 */
abstract class BaseActivity<VB : ViewBinding> : Activity() {
    lateinit var binding: VB

    /**
     * 批量显示或隐藏 [View]
     *
     * @param isShow [Boolean]
     * @param views [View]
     */
    open fun showView(isShow: Boolean = true, vararg views: View?) {
        for (element in views) {
            element?.visibility = if (isShow) View.VISIBLE else View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 安卓 15 以下仍需要
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkMode(this@BaseActivity)
            isAppearanceLightNavigationBars = !isDarkMode(this@BaseActivity)
        }

        // 通过反射绑定布局
        javaClass.genericSuperclass.also { type ->
            if (type is ParameterizedType) {
                val bindingClass = type.actualTypeArguments[0] as Class<*>
                val inflate = bindingClass.declaredMethods.firstOrNull {
                    it.name == "inflate" && it.parameterCount == 1 &&
                        it.parameterTypes[0] == android.view.LayoutInflater::class.java
                }?.apply { isAccessible = true } ?: error("binding failed")
                binding = inflate.invoke(null, layoutInflater) as VB
                setContentView(binding.root)
            } else error("binding but got wrong type")
        }

        /** 装载子类 */
        onCreate()
    }

    /** 回调 [onCreate] 方法 */
    abstract fun onCreate()
}