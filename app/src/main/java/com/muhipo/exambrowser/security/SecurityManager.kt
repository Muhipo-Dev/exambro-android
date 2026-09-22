package com.muhipo.exambrowser.security

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object SecurityManager {

    private val rehideHandler = Handler(Looper.getMainLooper())

    /**
     * Applies strict immersive sticky mode to force hide Android navigation bar & status bar.
     * Combines WindowInsetsControllerCompat, legacy System UI flags, and Display Cutout settings
     * for maximal compatibility across all OEM ROMs (Samsung, Xiaomi/HyperOS, Oppo, Vivo, Realme, Stock Android).
     */
    @Suppress("DEPRECATION")
    fun enableStrictImmersiveMode(window: Window) {
        // 1. Set display cutout mode to expand into cutout area on Android P+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            } catch (_: Exception) {}
        }

        // 2. Set window flags to keep screen on, dismiss keyguard, and show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        // 3. Enforce FLAG_SECURE by default to block screenshot & screen recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        val hideSystemBars = Runnable {
            try {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.hide(WindowInsetsCompat.Type.navigationBars())
                controller.hide(WindowInsetsCompat.Type.statusBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } catch (_: Exception) {}
        }

        hideSystemBars.run()

        // 4. Apply legacy system UI flags for additional lock protection on OEM ROMs
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )

        // 5. Continuously enforce system bar suppression when insets change or swipe occurs
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            if (insets.isVisible(WindowInsetsCompat.Type.navigationBars()) ||
                insets.isVisible(WindowInsetsCompat.Type.statusBars())
            ) {
                hideSystemBars.run()
            }
            WindowInsetsCompat.CONSUMED
        }

        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0 ||
                (visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0
            ) {
                rehideHandler.removeCallbacksAndMessages(null)
                rehideHandler.postDelayed(hideSystemBars, 50)
            }
        }
    }

    /**
     * Prevents taking screenshots, screen recordings, and hides content in recent tasks overview.
     */
    fun applyScreenshotProtection(window: Window, enable: Boolean) {
        if (enable) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /**
     * Configures WebView to disable long-click context menus, text dragging,
     * while preserving normal keyboard text entry in input/textarea fields.
     */
    fun secureWebViewInput(webView: WebView) {
        webView.isLongClickable = false
        webView.setOnLongClickListener { true }
        webView.isHapticFeedbackEnabled = false
        webView.setOnDragListener { _, _ -> true }
    }

    /**
     * Injects CSS & JavaScript protection rules to block text selection, copy/cut/paste,
     * translate popups, context menus, and developer shortcuts.
     */
    fun injectAntiCopyCss(webView: WebView) {
        val js = """
            (function() {
                // 1. Inject CSS rule for anti-copy/anti-selection/anti-callout
                var style = document.createElement('style');
                style.type = 'text/css';
                style.innerHTML = `
                    * {
                        -webkit-touch-callout: none !important;
                        -webkit-user-select: none !important;
                        user-select: none !important;
                    }
                    input, textarea, [contenteditable="true"] {
                        -webkit-user-select: text !important;
                        user-select: text !important;
                    }
                `;
                document.head.appendChild(style);

                // 2. Prevent right-click / context menu / translation popups
                document.addEventListener('contextmenu', function(e) {
                    e.preventDefault();
                    return false;
                }, true);

                // 3. Prevent drag & drop
                document.addEventListener('dragstart', function(e) {
                    e.preventDefault();
                    return false;
                }, true);

                // 4. Prevent text selection, copy, cut, paste
                document.addEventListener('selectstart', function(e) {
                    var tag = e.target.tagName;
                    if (tag !== 'INPUT' && tag !== 'TEXTAREA' && !e.target.isContentEditable) {
                        e.preventDefault();
                        return false;
                    }
                }, true);

                document.addEventListener('copy', function(e) {
                    var tag = e.target.tagName;
                    if (tag !== 'INPUT' && tag !== 'TEXTAREA' && !e.target.isContentEditable) {
                        e.preventDefault();
                        return false;
                    }
                }, true);

                document.addEventListener('cut', function(e) {
                    e.preventDefault();
                    return false;
                }, true);

                // 5. Block inspection / copy keyboard shortcuts (Ctrl+C, Ctrl+U, Ctrl+S, F12)
                document.addEventListener('keydown', function(e) {
                    if ((e.ctrlKey || e.metaKey) && (e.key === 'c' || e.key === 'u' || e.key === 's' || e.key === 'a' || e.key === 'p')) {
                        var tag = e.target.tagName;
                        if (tag !== 'INPUT' && tag !== 'TEXTAREA' && !e.target.isContentEditable) {
                            e.preventDefault();
                            return false;
                        }
                    }
                    if (e.key === 'F12') {
                        e.preventDefault();
                        return false;
                    }
                }, true);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }
}
