package com.muhipo.exambrowser.admin

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muhipo.exambrowser.R
import com.muhipo.exambrowser.databinding.ActivityAdminBinding
import com.muhipo.exambrowser.databinding.DialogAddDomainBinding
import com.muhipo.exambrowser.databinding.DialogChangePinBinding
import com.muhipo.exambrowser.kiosk.KioskManager
import com.muhipo.exambrowser.utils.PreferenceManager
import com.muhipo.exambrowser.utils.UrlValidator

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var kioskManager: KioskManager
    private lateinit var whitelistAdapter: WhitelistAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)
        kioskManager = KioskManager(this)

        setupEdgeToEdge()
        setupToolbar()
        setupDeviceOwnerStatus()
        setupWhitelistRecycler()
        setupToggles()
        setupActions()
        setupAppVersion()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
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

    private fun setupToolbar() {
        binding.toolbarAdmin.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupDeviceOwnerStatus() {
        val isDeviceOwner = kioskManager.isDeviceOwner()
        if (isDeviceOwner) {
            binding.imgDeviceOwnerStatus.setImageResource(R.drawable.ic_lock)
            binding.imgDeviceOwnerStatus.setColorFilter(ContextCompat.getColor(this, R.color.brand_green))
            binding.tvDeviceOwnerStatus.text = getString(R.string.admin_device_owner_active)
            binding.tvDeviceOwnerStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
            binding.tvDeviceOwnerHelp.text = "Device Owner is configured. Kiosk mode runs with zero bypass capability."
        } else {
            binding.imgDeviceOwnerStatus.setImageResource(R.drawable.ic_warning)
            binding.imgDeviceOwnerStatus.setColorFilter(ContextCompat.getColor(this, R.color.warning))
            binding.tvDeviceOwnerStatus.text = getString(R.string.admin_device_owner_inactive)
            binding.tvDeviceOwnerStatus.setTextColor(ContextCompat.getColor(this, R.color.warning))
        }
    }

    private fun setupWhitelistRecycler() {
        val domains = preferenceManager.getWhitelistDomains().toMutableList()
        whitelistAdapter = WhitelistAdapter(domains) { domainToDelete ->
            preferenceManager.removeWhitelistDomain(domainToDelete)
            refreshWhitelist()
            Toast.makeText(this, "Removed: $domainToDelete", Toast.LENGTH_SHORT).show()
        }

        binding.rvWhitelist.layoutManager = LinearLayoutManager(this)
        binding.rvWhitelist.adapter = whitelistAdapter

        binding.btnAddDomain.setOnClickListener {
            showAddDomainDialog()
        }
    }

    private fun refreshWhitelist() {
        val domains = preferenceManager.getWhitelistDomains().toList()
        whitelistAdapter.updateDomains(domains)
    }

    private fun showAddDomainDialog() {
        val dialogBinding = DialogAddDomainBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this, R.style.Theme_ExamBrowser_Dialog)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelAddDomain.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmAddDomain.setOnClickListener {
            val input = dialogBinding.etNewDomain.text.toString().trim().lowercase()
            val host = UrlValidator.extractHost(input) ?: input.replace("/", "")

            if (!host.isNullOrEmpty()) {
                preferenceManager.addWhitelistDomain(host)
                refreshWhitelist()
                dialog.dismiss()
                Toast.makeText(this, "Added: $host", Toast.LENGTH_SHORT).show()
            } else {
                dialogBinding.tvDomainError.visibility = View.VISIBLE
            }
        }

        dialog.show()
    }

    private fun setupToggles() {
        // Kiosk Mode Switch
        binding.switchKioskMode.isChecked = preferenceManager.isKioskEnabled
        binding.switchKioskMode.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.isKioskEnabled = isChecked
        }

        // Screenshot Protection Switch
        binding.switchScreenshot.isChecked = preferenceManager.isScreenshotProtectionEnabled
        binding.switchScreenshot.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.isScreenshotProtectionEnabled = isChecked
        }

        // Allow Back Switch
        binding.switchAllowBack.isChecked = preferenceManager.isBackAllowed
        binding.switchAllowBack.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.isBackAllowed = isChecked
        }

        // Allow Downloads Switch
        binding.switchAllowDownloads.isChecked = preferenceManager.isDownloadAllowed
        binding.switchAllowDownloads.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.isDownloadAllowed = isChecked
        }
    }

    private fun setupActions() {
        // Reset Active Session
        binding.btnResetSession.setOnClickListener {
            preferenceManager.clearExamSession()
            Toast.makeText(this, "Active exam session cleared.", Toast.LENGTH_SHORT).show()
        }

        // Change Admin PIN
        binding.btnChangePin.setOnClickListener {
            showChangePinDialog()
        }
    }

    private fun showChangePinDialog() {
        val dialogBinding = DialogChangePinBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this, R.style.Theme_ExamBrowser_Dialog)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelChangePin.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnConfirmChangePin.setOnClickListener {
            val currentPin = dialogBinding.etCurrentPin.text.toString().trim()
            val newPin = dialogBinding.etNewPin.text.toString().trim()

            if (!preferenceManager.verifyAdminPin(currentPin)) {
                dialogBinding.tvChangePinError.text = "Incorrect current PIN."
                dialogBinding.tvChangePinError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            if (newPin.length < 4) {
                dialogBinding.tvChangePinError.text = "New PIN must be at least 4 digits."
                dialogBinding.tvChangePinError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            preferenceManager.updateAdminPin(newPin)
            dialog.dismiss()
            Toast.makeText(this, "Admin PIN updated successfully.", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun setupAppVersion() {
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = pInfo.versionName ?: "1.6"
            val sdkInt = Build.VERSION.SDK_INT
            binding.tvAppInfo.text = "Ujian Muhipo v$versionName (Mendukung Android 7 s/d 16 | OS API $sdkInt)\nPackage: com.muhipo.exambrowser"
        } catch (_: Exception) {
            binding.tvAppInfo.text = "Ujian Muhipo v1.6 (Mendukung Android 7 s/d 16)\nPackage: com.muhipo.exambrowser"
        }
    }
}
