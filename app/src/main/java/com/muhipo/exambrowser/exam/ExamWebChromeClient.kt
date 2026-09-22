package com.muhipo.exambrowser.exam

import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView

class ExamWebChromeClient(
    private val onProgressUpdate: (Int) -> Unit
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressUpdate(newProgress)
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        // Automatically grant resources if the exam website requests camera/microphone for proctoring
        if (request != null) {
            request.grant(request.resources)
        }
    }
}
