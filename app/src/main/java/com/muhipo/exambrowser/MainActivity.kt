package com.muhipo.exambrowser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muhipo.exambrowser.databinding.ActivityMainBinding
import com.muhipo.exambrowser.databinding.DialogManualUrlBinding
import com.muhipo.exambrowser.exam.ExamActivity
import com.muhipo.exambrowser.kiosk.KioskManager
import com.muhipo.exambrowser.scanner.ScannerActivity
import com.muhipo.exambrowser.utils.PreferenceManager
import com.muhipo.exambrowser.utils.UrlValidator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var kioskManager: KioskManager

    private var logoTapCount = 0
    private var lastLogoTapTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)
        kioskManager = KioskManager(this)

        setupEdgeToEdge()
        setupButtons()
        setupLogoGesture()
        handleIncomingDeepLink(intent)
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        checkActiveSession()
        updateKioskBadge()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingDeepLink(intent)
    }

    private fun setupButtons() {
        // Big Button: SCAN QR / BARCODE
        binding.btnScanQr.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivity(intent)
        }

        // Small Button: ENTER URL MANUALLY
        binding.btnEnterUrlManually.setOnClickListener {
            showManualUrlDialog()
        }

        // Resume Active Session button
        binding.btnResumeExam.setOnClickListener {
            preferenceManager.activeExamUrl?.let { url ->
                launchExam(url)
            }
        }
    }

    /**
     * Tekan logo SMA MUHIPO 3 kali untuk langsung membuka dialog Input IP/URL Manual.
     */
    private fun setupLogoGesture() {
        binding.logoContainer.setOnClickListener {
            val currentTime = SystemClock.uptimeMillis()
            if (currentTime - lastLogoTapTime < 3000) {
                logoTapCount++
            } else {
                logoTapCount = 1
            }
            lastLogoTapTime = currentTime

            if (logoTapCount >= 3) {
                logoTapCount = 0
                showManualUrlDialog()
            }
        }
    }

    private fun showManualUrlDialog() {
        val dialogBinding = DialogManualUrlBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this, R.style.Theme_ExamBrowser_Dialog)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelManualUrl.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSubmitManualUrl.setOnClickListener {
            val rawInput = dialogBinding.etExamUrl.text.toString().trim()
            val validUrl = UrlValidator.normalizeAndValidateUrl(rawInput)
            if (validUrl != null) {
                dialog.dismiss()
                // Whitelist domain/IP and launch
                val host = UrlValidator.extractHost(validUrl)
                if (!host.isNullOrEmpty()) {
                    preferenceManager.addWhitelistDomain(host)
                }
                preferenceManager.startExamSession(validUrl)
                launchExam(validUrl)
            } else {
                dialogBinding.tvUrlError.visibility = View.VISIBLE
            }
        }

        dialog.show()
    }

    private fun launchExam(url: String) {
        val intent = Intent(this, ExamActivity::class.java).apply {
            putExtra(ExamActivity.EXTRA_EXAM_URL, url)
        }
        startActivity(intent)
    }

    private fun checkActiveSession() {
        if (preferenceManager.isExamActive && !preferenceManager.activeExamUrl.isNullOrEmpty()) {
            val url = preferenceManager.activeExamUrl!!
            binding.cardActiveSession.visibility = View.VISIBLE
            binding.tvActiveSessionUrl.text = url

            // Auto-relaunch active session immediately
            launchExam(url)
        } else {
            binding.cardActiveSession.visibility = View.GONE
        }
    }

    private fun updateKioskBadge() {
        if (kioskManager.isDeviceOwner()) {
            binding.tvKioskStatus.text = getString(R.string.status_device_owner_mode)
            binding.tvKioskStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
        } else {
            binding.tvKioskStatus.text = getString(R.string.status_restricted_mode)
            binding.tvKioskStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_accent))
        }
    }

    private fun handleIncomingDeepLink(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && data.scheme.equals("exam", ignoreCase = true) && data.host.equals("start", ignoreCase = true)) {
            val rawParam = data.getQueryParameter("url")
            val targetUrl = UrlValidator.normalizeAndValidateUrl(rawParam)
            if (targetUrl != null) {
                val host = UrlValidator.extractHost(targetUrl)
                if (!host.isNullOrEmpty()) {
                    preferenceManager.addWhitelistDomain(host)
                }
                preferenceManager.startExamSession(targetUrl)
                launchExam(targetUrl)
            } else {
                Toast.makeText(this, R.string.error_invalid_exam_code, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
