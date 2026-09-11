package com.gswxxn.restoresplashscreen.ui.subsettings

import android.widget.LinearLayout
import com.gswxxn.restoresplashscreen.R
import com.gswxxn.restoresplashscreen.databinding.ActivitySubSettingsBinding
import com.gswxxn.restoresplashscreen.ui.SubSettings
import com.gswxxn.restoresplashscreen.ui.`interface`.ISubSettings
import com.gswxxn.restoresplashscreen.utils.BlockMIUIHelper.addBlockMIUIView
import com.gswxxn.restoresplashscreen.view.BlockMIUIItemData

/**
 * Hook 信息界面
 *
 * 仅展示本版本预期的 Hook 点 (Android 17 / HyperOS 4 真机签名),
 * 实际命中情况请查看 LSPosed 日志
 */
object HookInfo : ISubSettings {
    override val titleID = R.string.hook_info
    override val demoImageID = null

    private val points = listOf(
        "android" to listOf(
            "com.android.server.wm.ActivityRecord#validateStartingWindowTheme",
            "com.android.server.wm.ActivityRecord#showStartingWindow",
            "com.android.server.wm.ActivityRecord#getStartingWindowType"
        ),
        "com.android.systemui" to listOf(
            "SplashscreenContentDrawer#makeSplashScreenContentView(Context, StartingWindowInfo, int, Consumer)",
            "SplashscreenContentDrawer#getWindowAttrs",
            "SplashscreenContentDrawer#getBGColorFromCache",
            "SplashscreenContentDrawer\$SplashViewBuilder#<init>",
            "SplashscreenContentDrawer\$SplashViewBuilder#createIconDrawable(Drawable, boolean, boolean)",
            "SplashscreenContentDrawer\$SplashViewBuilder#build",
            "SplashscreenContentDrawer\$HighResIconProvider#getIcon(ActivityInfo, int, int)",
            "com.android.miui.launcher3x.icons.IconProvider#getIcon",
            "com.android.miui.launcher3x.icons.BaseIconFactory#createIconBitmap",
            "com.android.miui.launcher3x.icons.BaseIconFactory#wrapToAdaptiveIcon",
            "SplashscreenContentDrawer\$ColorCache\$IconColor#<init>",
            "ShellTaskOrganizer#removeStartingWindow",
            "StartingWindowController#removeStartingWindow",
            "android.app.TaskSnapshotHelperImpl#isMiuiHome"
        )
    )

    override fun create(context: SubSettings, binding: ActivitySubSettingsBinding): BlockMIUIItemData.() -> Unit = {
        redrawView(context, binding.settingItems)
    }

    private fun redrawView(context: SubSettings, linearLayout: LinearLayout) {
        linearLayout.removeAllViews()
        linearLayout.addBlockMIUIView(context) {
            points.forEach { (scope, methods) ->
                TitleText(text = scope)
                methods.forEach { method ->
                    TextSummary(text = method)
                }
            }
        }
    }
}
