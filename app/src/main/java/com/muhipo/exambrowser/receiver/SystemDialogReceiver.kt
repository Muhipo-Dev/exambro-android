package com.muhipo.exambrowser.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver to catch system events like Home button press, Recent Apps button,
 * and Status Bar notification shade pulls (ACTION_CLOSE_SYSTEM_DIALOGS).
 */
class SystemDialogReceiver(
    private val onSystemNavigationAttempted: (reason: String?) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
            val reason = intent.getStringExtra("reason")
            onSystemNavigationAttempted(reason)
        }
    }
}
