package com.muhipo.exambrowser.exam

import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.ActionMode
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.muhipo.exambrowser.MainActivity
import com.muhipo.exambrowser.R
import com.muhipo.exambrowser.audio.PoliceSirenPlayer
import com.muhipo.exambrowser.cache.CbtCacheScriptInjector
import com.muhipo.exambrowser.cache.IndexedDBJavascriptInterface
import com.muhipo.exambrowser.databinding.ActivityExamBinding
import com.muhipo.exambrowser.databinding.DialogFinishExamBinding
import com.muhipo.exambrowser.kiosk.KioskManager
import com.muhipo.exambrowser.receiver.SystemDialogReceiver
import com.muhipo.exambrowser.security.SecurityManager
import com.muhipo.exambrowser.utils.NetworkUtils
import com.muhipo.exambrowser.utils.PreferenceManager
import com.muhipo.exambrowser.utils.UrlValidator

class ExamActivity : AppCompatActivity(), ExamWebClient.Listener {

    private lateinit var binding: ActivityExamBinding
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var kioskManager: KioskManager
    private lateinit var networkUtils: NetworkUtils

    private var currentExamUrl: String = ""
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Super Loud Police Siren & Hazard Buzzer Control
    private var policeSirenPlayer: PoliceSirenPlayer = PoliceSirenPlayer()
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var buzzerHandler: Handler? = null
    private var buzzerRunnable: Runnable? = null
    private var isBuzzerActive = false
    private var activeFinishDialog: AlertDialog? = null
    private var systemDialogReceiver: SystemDialogReceiver? = null

    companion object {
        const val EXTRA_EXAM_URL = "extra_exam_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)
        kioskManager = KioskManager(this)
        networkUtils = NetworkUtils(this)

        currentExamUrl = intent.getStringExtra(EXTRA_EXAM_URL) ?: preferenceManager.activeExamUrl ?: ""

        // Memastikan domain/IP server ujian langsung masuk ke whitelist
        val host = UrlValidator.extractHost(currentExamUrl)
        if (!host.isNullOrEmpty()) {
            preferenceManager.addWhitelistDomain(host)
        }

        // Langsung ikat proses ke jaringan WiFi jika sudah tersambung
        networkUtils.forceBindToCurrentWifi()

        setupFullscreenAndSecurity()
        setupLockTaskKiosk()
        setupBackHandler()
        setupWebView()
        setupButtons()
        monitorNetwork()

        loadExamUrl()
    }

    private fun setupFullscreenAndSecurity() {
        // Apply strict immersive sticky mode to force hide navigation bar & status bar
        SecurityManager.enableStrictImmersiveMode(window)

        // Apply screenshot & screen recording protection
        SecurityManager.applyScreenshotProtection(
            window,
            preferenceManager.isScreenshotProtectionEnabled
        )

        ViewCompat.setOnApplyWindowInsetsListener(binding.topNavbar) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val topInset = maxOf(statusBars.top, displayCutout.top)
            view.updatePadding(top = topInset)
            insets
        }
    }

    private fun setupLockTaskKiosk() {
        if (preferenceManager.isKioskEnabled && !kioskManager.isInLockTaskMode()) {
            kioskManager.startKiosk(this)
        }
        SecurityManager.enableStrictImmersiveMode(window)
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (preferenceManager.isBackAllowed && binding.examWebView.canGoBack()) {
                    binding.examWebView.goBack()
                } else {
                    Toast.makeText(this@ExamActivity, R.string.back_disabled_msg, Toast.LENGTH_SHORT).show()
                    if (preferenceManager.isExamActive && !isFinishing) {
                        playSuperLoudPoliceSirenAndBuzzer()
                        showFinishConfirmationDialog()
                    }
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.examWebView
        val settings = webView.settings

        // Akselerasi Hardware GPU agar render CBT super cepat, mulus & ringan di HP spek rendah
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        // Konfigurasi performa tinggi & hemat RAM/CPU/Baterai
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setGeolocationEnabled(false)
        settings.savePassword = false
        settings.builtInZoomControls = false
        settings.displayZoomControls = false

        // Mode cache tercepat untuk koneksi intranet lokal
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Pengaturan wajib untuk kompatibilitas server ujian lokal intranet (Moodle, Candy CBT, BeeSMART, dll)
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        @Suppress("DEPRECATION")
        settings.databaseEnabled = true
        @Suppress("DEPRECATION")
        settings.databasePath = applicationContext.getDir("databases", MODE_PRIVATE).path

        // IndexedDB & Client Cache Setup
        if (preferenceManager.isIndexedDbCacheEnabled) {
            val jsBridge = IndexedDBJavascriptInterface(this) { isPending, statusMsg ->
                runOnUiThread {
                    showCacheSyncBanner(isPending, statusMsg)
                }
            }
            webView.addJavascriptInterface(jsBridge, "ExamBrowserCacheBridge")
        }

        // Cookie management
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // Security controls
        SecurityManager.secureWebViewInput(webView)

        // Download handling
        webView.setDownloadListener { _, _, _, _, _ ->
            if (!preferenceManager.isDownloadAllowed) {
                Toast.makeText(this, R.string.download_blocked_msg, Toast.LENGTH_SHORT).show()
            }
        }

        // Custom Clients
        webView.webViewClient = ExamWebClient(
            allowedDomainsProvider = { preferenceManager.getWhitelistDomains() },
            listener = this
        )

        webView.webChromeClient = ExamWebChromeClient { progress ->
            binding.progressBar.progress = progress
            if (progress in 1..99) {
                binding.progressBar.visibility = View.VISIBLE
            } else {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun setupButtons() {
        // Top Navbar Refresh Page Button
        binding.btnRefreshWeb.setOnClickListener {
            binding.examWebView.reload()
            Toast.makeText(this, R.string.btn_refresh_page, Toast.LENGTH_SHORT).show()
        }

        // Top Navbar Exit Exam Button
        binding.btnExitExam.setOnClickListener {
            showFinishConfirmationDialog()
        }

        binding.btnRetryOffline.setOnClickListener {
            if (networkUtils.isOnline()) {
                binding.layoutOffline.visibility = View.GONE
                loadExamUrl()
            } else {
                Toast.makeText(this, R.string.offline_desc, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRetryError.setOnClickListener {
            binding.layoutError.visibility = View.GONE
            loadExamUrl()
        }
    }

    private fun monitorNetwork() {
        networkCallback = networkUtils.registerNetworkCallback(
            onNetworkAvailable = {
                runOnUiThread {
                    if (binding.layoutOffline.visibility == View.VISIBLE || binding.layoutError.visibility == View.VISIBLE || binding.examWebView.url.isNullOrEmpty() || binding.examWebView.url == "about:blank") {
                        binding.layoutOffline.visibility = View.GONE
                        binding.layoutError.visibility = View.GONE
                        loadExamUrl()
                    } else {
                        // Terhubung kembali saat WebView aktif -> Trigger IndexedDB auto-release sync!
                        showCacheSyncBanner(false, getString(R.string.cache_banner_reconnecting))
                        CbtCacheScriptInjector.triggerSyncRelease(binding.examWebView)
                        binding.layoutCacheBanner.postDelayed({
                            showCacheSyncBanner(false, getString(R.string.cache_banner_synced))
                            hideCacheSyncBannerDelayed()
                        }, 2500)
                    }
                }
            },
            onNetworkLost = {
                runOnUiThread {
                    // Saat koneksi terputus ditengah ujian, jika WebView sedang menampilkan soal, jangan tutup WebView.
                    // Tampilkan banner floating top agar siswa tetap dapat menjawab soal offline dengan IndexedDB!
                    if (!binding.examWebView.url.isNullOrEmpty() && binding.examWebView.url != "about:blank" && binding.layoutError.visibility != View.VISIBLE) {
                        showCacheSyncBanner(true, getString(R.string.cache_banner_offline))
                    } else {
                        binding.layoutOffline.visibility = View.VISIBLE
                    }
                }
            }
        )
    }

    private fun showCacheSyncBanner(isWarning: Boolean, message: String) {
        binding.layoutCacheBanner.visibility = View.VISIBLE
        binding.tvCacheBannerText.text = message
        if (isWarning) {
            binding.imgCacheBannerIcon.setImageResource(R.drawable.ic_warning)
            binding.imgCacheBannerIcon.setColorFilter(ContextCompat.getColor(this, R.color.brand_gold))
        } else {
            binding.imgCacheBannerIcon.setImageResource(R.drawable.ic_refresh)
            binding.imgCacheBannerIcon.setColorFilter(ContextCompat.getColor(this, R.color.brand_green))
        }
    }

    private fun hideCacheSyncBannerDelayed() {
        binding.layoutCacheBanner.postDelayed({
            binding.layoutCacheBanner.visibility = View.GONE
        }, 3500)
    }

    private fun loadExamUrl() {
        if (!networkUtils.isOnline()) {
            binding.layoutOffline.visibility = View.VISIBLE
            return
        }

        val normalized = UrlValidator.normalizeAndValidateUrl(currentExamUrl)
        if (!normalized.isNullOrEmpty()) {
            currentExamUrl = normalized
            binding.layoutOffline.visibility = View.GONE
            binding.layoutError.visibility = View.GONE
            // Langsung muat URL di WebView tanpa ping ke server mana pun
            binding.examWebView.loadUrl(currentExamUrl)
        } else {
            Toast.makeText(this, R.string.error_invalid_url, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // --- Loud Warning Alarm Police Siren, Buzzer & Volume Control ---

    private fun playSuperLoudPoliceSirenAndBuzzer() {
        if (isBuzzerActive) return
        isBuzzerActive = true

        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

            // 1. Force ONLY Alarm stream (STREAM_ALARM) to 100% MAXIMUM VOLUME
            try {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
            } catch (_: Exception) {}

            // 2. Play High-Decibel Police Siren Pitch Sweep Synthesizer
            policeSirenPlayer.start()

            // 3. Play Raw Hazard Buzzer Audio Source (res/raw/buzzer_alarm.wav)
            mediaPlayer = MediaPlayer.create(this, R.raw.buzzer_alarm)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                start()
            }

            // 4. High-Pitched Emergency Ringback Tone Generator on Alarm Stream
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            buzzerHandler = Handler(Looper.getMainLooper())
            buzzerRunnable = object : Runnable {
                override fun run() {
                    if (isBuzzerActive) {
                        try {
                            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 450)
                        } catch (_: Exception) {}
                        buzzerHandler?.postDelayed(this, 600)
                    }
                }
            }
            buzzerHandler?.post(buzzerRunnable!!)

            // 5. Heavy Pulsing Haptic Vibration
            vibrator = ContextCompat.getSystemService(this, Vibrator::class.java)
            val pattern = longArrayOf(0, 500, 200, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playSuperLoudBuzzer() {
        playSuperLoudPoliceSirenAndBuzzer()
    }

    private fun stopSuperLoudPoliceSirenAndBuzzer() {
        if (!isBuzzerActive) return
        isBuzzerActive = false

        try {
            policeSirenPlayer.stop()

            buzzerRunnable?.let { buzzerHandler?.removeCallbacks(it) }
            buzzerHandler = null
            buzzerRunnable = null

            toneGenerator?.stopTone()
            toneGenerator?.release()
            toneGenerator = null

            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopSuperLoudBuzzer() {
        stopSuperLoudPoliceSirenAndBuzzer()
    }

    // --- ExamWebClient.Listener Callbacks ---

    override fun onPageLoading(isPageLoading: Boolean) {
        binding.progressBar.visibility = if (isPageLoading) View.VISIBLE else View.GONE
    }

    override fun onDomainBlocked(blockedUrl: String) {
        val host = UrlValidator.extractHost(blockedUrl) ?: blockedUrl
        Snackbar.make(
            binding.examRoot,
            "${getString(R.string.error_outside_domain)} ($host)",
            Snackbar.LENGTH_LONG
        ).show()
        if (preferenceManager.isExamActive && !isFinishing) {
            playSuperLoudPoliceSirenAndBuzzer()
            showFinishConfirmationDialog()
        }
    }

    override fun onLoadFailed(errorCode: Int, description: String) {
        binding.layoutError.visibility = View.VISIBLE
        binding.tvErrorDetail.text = "$description (Code $errorCode)"
    }

    override fun onSslErrorOccurred(handler: SslErrorHandler, error: SslError) {
        // Tampilkan dialog SSL yang mengizinkan sertifikat self-signed pada server ujian lokal
        MaterialAlertDialogBuilder(this)
            .setTitle("Sertifikat SSL Server Ujian")
            .setMessage("Sertifikat SSL server ujian lokal tidak terverifikasi (Self-Signed). Tetap lanjutkan ke halaman ujian?")
            .setPositiveButton("LANJUTKAN") { _, _ -> handler.proceed() }
            .setNegativeButton("BATAL") { _, _ -> handler.cancel() }
            .setCancelable(false)
            .show()
    }

    private fun showFinishConfirmationDialog() {
        // Trigger super loud Police Siren & hazard buzzer alarm at 100% volume
        playSuperLoudPoliceSirenAndBuzzer()

        if (activeFinishDialog?.isShowing == true) {
            return
        }

        val dialogBinding = DialogFinishExamBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this, R.style.Theme_ExamBrowser_Dialog)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.btnCancelFinish.setOnClickListener {
            stopSuperLoudPoliceSirenAndBuzzer()
            dialog.dismiss()
            activeFinishDialog = null
        }

        dialogBinding.btnConfirmFinish.setOnClickListener {
            stopSuperLoudPoliceSirenAndBuzzer()
            dialog.dismiss()
            activeFinishDialog = null
            finishExamAndExit()
        }

        activeFinishDialog = dialog
        dialog.show()
    }

    private fun finishExamAndExit() {
        // 1. Clear session
        preferenceManager.clearExamSession()

        // 2. Clear WebView cache and history
        binding.examWebView.clearCache(true)
        binding.examWebView.clearHistory()
        binding.examWebView.loadUrl("about:blank")

        // 3. Stop Lock Task if running
        kioskManager.stopKiosk(this)

        // 4. Return to MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        SecurityManager.enableStrictImmersiveMode(window)
        registerSystemDialogReceiver()
        if (preferenceManager.isKioskEnabled && !kioskManager.isInLockTaskMode()) {
            kioskManager.startKiosk(this)
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        SecurityManager.enableStrictImmersiveMode(window)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        SecurityManager.enableStrictImmersiveMode(window)
        return super.dispatchTouchEvent(ev)
    }

    @Suppress("DEPRECATION")
    private fun registerSystemDialogReceiver() {
        if (systemDialogReceiver == null) {
            systemDialogReceiver = SystemDialogReceiver {
                if (preferenceManager.isExamActive && !isFinishing) {
                    SecurityManager.enableStrictImmersiveMode(window)
                    playSuperLoudPoliceSirenAndBuzzer()
                    showFinishConfirmationDialog()
                }
            }
            try {
                ContextCompat.registerReceiver(
                    this,
                    systemDialogReceiver,
                    IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                    ContextCompat.RECEIVER_EXPORTED
                )
            } catch (_: Exception) {}
        }
    }

    private fun unregisterSystemDialogReceiver() {
        systemDialogReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            systemDialogReceiver = null
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (preferenceManager.isExamActive && !isFinishing) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_APP_SWITCH,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_SEARCH -> {
                    SecurityManager.enableStrictImmersiveMode(window)
                    playSuperLoudPoliceSirenAndBuzzer()
                    showFinishConfirmationDialog()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isBuzzerActive && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE)) {
            // Force maximum volume on Alarm stream back immediately
            try {
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
            } catch (_: Exception) {}
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Home button or Recent Apps button pressed during exam
        if (preferenceManager.isExamActive && !isFinishing) {
            SecurityManager.enableStrictImmersiveMode(window)
            playSuperLoudPoliceSirenAndBuzzer()
            showFinishConfirmationDialog()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            SecurityManager.enableStrictImmersiveMode(window)
        } else if (preferenceManager.isExamActive && !isFinishing) {
            // Lost focus due to app switching, split screen, popup, or force minimize
            SecurityManager.enableStrictImmersiveMode(window)
            playSuperLoudPoliceSirenAndBuzzer()
            showFinishConfirmationDialog()
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
        if (preferenceManager.isExamActive && !isFinishing) {
            SecurityManager.enableStrictImmersiveMode(window)
            playSuperLoudPoliceSirenAndBuzzer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (preferenceManager.isExamActive && !isFinishing) {
            SecurityManager.enableStrictImmersiveMode(window)
            playSuperLoudPoliceSirenAndBuzzer()
        }
    }

    override fun onActionModeStarted(mode: ActionMode?) {
        mode?.finish()
        super.onActionModeStarted(mode)
    }

    override fun onDestroy() {
        stopSuperLoudPoliceSirenAndBuzzer()
        unregisterSystemDialogReceiver()
        CookieManager.getInstance().flush()
        super.onDestroy()
        networkCallback?.let { networkUtils.unregisterNetworkCallback(it) }
        binding.examWebView.destroy()
    }
}
