package com.gswxxn.restoresplashscreen.ui

import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.core.view.WindowInsetsCompat
import cn.fkj233.ui.activity.dp2px
import cn.fkj233.ui.activity.view.ImageTextV
import cn.fkj233.ui.activity.view.LineV
import com.gswxxn.restoresplashscreen.BuildConfig
import com.gswxxn.restoresplashscreen.R
import com.gswxxn.restoresplashscreen.data.ConstValue
import com.gswxxn.restoresplashscreen.data.DataConst
import com.gswxxn.restoresplashscreen.databinding.ActivityMainSettingsBinding
import com.gswxxn.restoresplashscreen.utils.BlockMIUIHelper.addBlockMIUIView
import com.gswxxn.restoresplashscreen.utils.CommonUtils.execShell
import com.gswxxn.restoresplashscreen.utils.CommonUtils.toast
import com.gswxxn.restoresplashscreen.utils.GraphicUtils.shrinkIcon
import com.gswxxn.restoresplashscreen.utils.Prefs
import com.gswxxn.restoresplashscreen.utils.prefs
import com.gswxxn.restoresplashscreen.view.NewMIUIDialog

/** 主界面 */
class MainSettingsActivity : BaseActivity<ActivityMainSettingsBinding>() {
    private var isReady = false

    private var devSettingsView: View? = null

    override fun onCreate() {
        binding.root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (isReady) binding.root.viewTreeObserver.removeOnPreDrawListener(this)
                return isReady
            }
        })

        Thread.sleep(400)
        isReady = true

        // 连接 LSPosed 远端配置服务
        Prefs.ensureService(this)
        Prefs.onServiceChanged = { runOnUiThread { refreshState() } }

        // 显示版本号
        binding.mainTextVersion.text = getString(R.string.module_version, BuildConfig.VERSION_NAME)

        // 重启UI
        binding.titleRestartIcon.setOnClickListener {
            NewMIUIDialog(this) {
                setTitle(R.string.restart_title)
                setMessage(R.string.restart_message)
                Button(getString(R.string.reboot)) {
                    execShell("reboot")
                    Thread.sleep(300)
                    toast(getString(R.string.no_root))
                }
                Button(getString(R.string.restart_system_ui)) {
                    execShell("pkill -f com.android.systemui && pkill -f com.gswxxn.restoresplashscreen")
                    Thread.sleep(300)
                    toast(getString(R.string.no_root))
                }
                Button(getString(R.string.button_cancel), cancelStyle = true) {
                    dismiss()
                }
            }.show()
        }

        // 关于页面
        binding.titleAboutPage.setOnClickListener {
            val intent = Intent(this, AboutPageActivity::class.java)
            startActivity(intent)
        }

        binding.settingsEntry.addBlockMIUIView(this) {
            fun line() = CustomView(LineV().create(this@MainSettingsActivity, null).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp2px(context, 0.9f))
                    .apply {
                        setMargins(dp2px(context, 20f), dp2px(context, 23f), 0, dp2px(context, 23f))
                    }
            })

            Author(shrinkIcon(R.drawable.ic_setting), getString(R.string.basic_settings), null, 0f, {
                startActivity(Intent(this@MainSettingsActivity, SubSettings::class.java).apply {
                    putExtra(ConstValue.EXTRA_MESSAGE, ConstValue.BASIC_SETTINGS)
                })
            })

            line()

            Author(shrinkIcon(R.drawable.ic_app), getString(R.string.custom_scope_settings), null, 0f, {
                startActivity(Intent(this@MainSettingsActivity, SubSettings::class.java).apply {
                    putExtra(ConstValue.EXTRA_MESSAGE, ConstValue.CUSTOM_SCOPE_SETTINGS)
                })
            })

            Author(shrinkIcon(R.drawable.ic_picture), getString(R.string.icon_settings), null, 0f, {
                startActivity(Intent(this@MainSettingsActivity, SubSettings::class.java).apply {
                    putExtra(ConstValue.EXTRA_MESSAGE, ConstValue.ICON_SETTINGS)
                })
            })

            Author(shrinkIcon(R.drawable.ic_bottom), getString(R.string.bottom_settings), null, 0f, {
                startActivity(Intent(this@MainSettingsActivity, SubSettings::class.java).apply {
                    putExtra(ConstValue.EXTRA_MESSAGE, ConstValue.BOTTOM_SETTINGS)
                })
            })

            Author(shrinkIcon(R.drawable.ic_color), getString(R.string.background_settings), null, 0f, {
                startActivity(Intent(this@MainSettingsActivity, SubSettings::class.java).apply {
                    putExtra(ConstValue.EXTRA_MESSAGE, ConstValue.BACKGROUND_SETTINGS)
                })
            })

            Author(shrinkIcon(R.drawable.ic_monitor), getString(R.string.display_settings), null, 0f, {
                startActivity(Intent(this@MainSettingsActivity, SubSettings::class.java).apply {
                    putExtra(ConstValue.EXTRA_MESSAGE, ConstValue.DISPLAY_SETTINGS)
                })
            })

            CustomView(
                ImageTextV(shrinkIcon(R.drawable.ic_lab), getString(R.string.dev_settings), null, 0f)
                    .create(this@MainSettingsActivity, null)
                    .also {
                        it.setOnClickListener {
                            startActivity(Intent(this@MainSettingsActivity, SubSettings::class.java).apply {
                                putExtra(ConstValue.EXTRA_MESSAGE, ConstValue.DEV_SETTINGS)
                            })
                        }
                        devSettingsView = it
                    }
            )

            line()

            Author(shrinkIcon(R.drawable.ic_help), getString(R.string.faq), null, 0f, {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.faq_url))))
            })
        }
    }

    private fun refreshState() {
        val active = Prefs.isModuleActive
        // 作用域缺失则提示需要配置
        val scopeOk = Prefs.scope.isEmpty() ||
            ("com.android.systemui" in Prefs.scope && "android" in Prefs.scope)
        val takeAction = active && !scopeOk
        binding.mainStatus.setBackgroundResource(
            when {
                active && takeAction -> R.drawable.bg_yellow_round
                active -> R.drawable.bg_green_round
                else -> R.drawable.bg_dark_round
            }
        )
        binding.mainImgStatus.setImageResource(
            when {
                active && !takeAction -> R.drawable.ic_success
                else -> R.drawable.ic_warn
            }
        )
        binding.mainTextStatus.text =
            when {
                active && takeAction ->
                    getString(R.string.module_is_updated, getString(R.string.system_ui))

                active -> getString(R.string.module_is_active)
                else -> getString(R.string.module_is_not_active)
            }
        showView(active, binding.mainTextApiWay)
        binding.mainTextApiWay.text = getString(
            R.string.xposed_framework_version,
            Prefs.frameworkName.ifBlank { "LSPosed" },
            Prefs.apiVersion
        )
    }

    override fun onResume() {
        super.onResume()
        Prefs.ensureService(this)
        refreshState()

        devSettingsView?.visibility =
            if (prefs().get(DataConst.ENABLE_DEV_SETTINGS)) View.VISIBLE else View.GONE
    }
}
