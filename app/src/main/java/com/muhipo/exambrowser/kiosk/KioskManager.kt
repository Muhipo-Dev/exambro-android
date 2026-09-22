package com.muhipo.exambrowser.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.muhipo.exambrowser.receiver.ExamDeviceAdminReceiver

class KioskManager(private val context: Context) {

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val activityManager: ActivityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val adminComponent = ExamDeviceAdminReceiver.getComponentName(context)

    companion object {
        private const val TAG = "KioskManager"
    }

    /**
     * Checks if this app is configured as the Device Owner.
     */
    fun isDeviceOwner(): Boolean {
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    /**
     * Checks if Lock Task Mode is permitted without user prompt.
     */
    fun isLockTaskPermitted(): Boolean {
        return dpm.isLockTaskPermitted(context.packageName)
    }

    /**
     * Checks if the device is currently in Lock Task Mode.
     */
    fun isInLockTaskMode(): Boolean {
        return activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    /**
     * Configures strict Device Owner policy restrictions to block navigation bars,
     * notification shade pull-down, home/recents buttons, and lock screen.
     */
    fun setupDeviceOwnerLockTask() {
        if (isDeviceOwner()) {
            try {
                // 1. Set our package as allowed for Lock Task without prompt
                dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))

                // 2. Disable Home, Overview/Recents, Notifications, System Info, Keyguard (Android 9+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.setLockTaskFeatures(adminComponent, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
                }

                // 3. Disable Status Bar / Notification shade pull-down
                dpm.setStatusBarDisabled(adminComponent, true)

                // 4. Disable Keyguard lockscreen during exam
                dpm.setKeyguardDisabled(adminComponent, true)

                Log.d(TAG, "Strict Device Owner Kiosk policies configured successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error configuring Device Owner Lock Task policies: ${e.message}", e)
            }
        }
    }

    /**
     * Starts strict Lock Task / Kiosk Mode on the given activity.
     */
    fun startKiosk(activity: Activity): Boolean {
        return try {
            if (isDeviceOwner()) {
                setupDeviceOwnerLockTask()
            }
            activity.startLockTask()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start lock task: ${e.message}", e)
            false
        }
    }

    /**
     * Stops Lock Task Mode and unlocks device policy restrictions.
     */
    fun stopKiosk(activity: Activity): Boolean {
        return try {
            if (isDeviceOwner()) {
                try {
                    dpm.setStatusBarDisabled(adminComponent, false)
                    dpm.setKeyguardDisabled(adminComponent, false)
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring Device Owner policies: ${e.message}", e)
                }
            }
            if (isInLockTaskMode()) {
                activity.stopLockTask()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop lock task: ${e.message}", e)
            false
        }
    }
}
