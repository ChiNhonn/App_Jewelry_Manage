package com.example.jewelrymanagement

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class JewelryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Force Light Mode across all devices to prevent white-on-white text issues in Dark Mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        registerActivityLifecycleCallbacks(SystemBarsPadding)
    }

    /**
     * Android 15+ always draws apps edge-to-edge, so every screen would slide under the status bar
     * and the gesture/navigation bar. Pad each activity's content by the system bar (and keyboard)
     * insets once, after setContentView has run.
     */
    private object SystemBarsPadding : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            val content = activity.findViewById<View>(android.R.id.content) ?: return
            if (content.getTag(R.id.tag_system_bars_padding) == true) return
            content.setTag(R.id.tag_system_bars_padding, true)
            ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
                WindowInsetsCompat.CONSUMED
            }
            ViewCompat.requestApplyInsets(content)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
