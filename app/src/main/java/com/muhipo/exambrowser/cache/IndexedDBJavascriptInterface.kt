package com.muhipo.exambrowser.cache

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject

/**
 * JavaScript Bridge exposed to WebView under window.ExamBrowserCacheBridge.
 * Provides bidirectional synchronization between browser-side IndexedDB/localStorage
 * and native Android SQLite storage.
 */
class IndexedDBJavascriptInterface(
    context: Context,
    private val onSyncStatusChanged: ((Boolean, String) -> Unit)? = null
) {

    private val cacheManager = IndexedDBCacheManager.getInstance(context)

    companion object {
        private const val TAG = "ExambroCacheBridge"
    }

    /**
     * Saves a CBT student answer from JavaScript to native persistent database.
     */
    @JavascriptInterface
    fun saveCbtAnswer(host: String, questionId: String, answerJson: String) {
        try {
            cacheManager.saveAnswer(host, questionId, answerJson)
            Log.d(TAG, "Answer saved natively for host=$host, qId=$questionId")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving answer natively", e)
        }
    }

    /**
     * Saves general IndexedDB store entries from JS to native persistent database.
     */
    @JavascriptInterface
    fun saveIndexedDbData(host: String, storeName: String, key: String, valueJson: String) {
        try {
            cacheManager.saveIndexedDbEntry(host, storeName, key, valueJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving IndexedDB data natively", e)
        }
    }

    /**
     * Retrieves an IndexedDB entry saved in native database.
     */
    @JavascriptInterface
    fun getIndexedDbData(host: String, storeName: String, key: String): String {
        return try {
            cacheManager.getIndexedDbEntry(host, storeName, key) ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error reading IndexedDB data natively", e)
            ""
        }
    }

    /**
     * Retrieves all saved CBT answers for a host as a JSON object.
     */
    @JavascriptInterface
    fun getAllSavedAnswers(host: String): String {
        return try {
            val map = cacheManager.getAllAnswers(host)
            val json = JSONObject()
            for ((key, value) in map) {
                json.put(key, value)
            }
            json.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all saved answers", e)
            "{}"
        }
    }

    /**
     * Queues an offline POST/PUT submission payload when server CBT connection is down.
     */
    @JavascriptInterface
    fun queueOfflinePayload(host: String, endpoint: String, method: String, payloadJson: String): String {
        return try {
            val reqId = cacheManager.queueOfflineRequest(host, endpoint, method, payloadJson)
            onSyncStatusChanged?.invoke(true, "Offline: Jawaban disimpan di queue ($reqId)")
            reqId
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing offline request", e)
            ""
        }
    }

    /**
     * Gets all queued offline requests for a host as a JSON array string.
     */
    @JavascriptInterface
    fun getQueuedRequests(host: String): String {
        return try {
            val requests = cacheManager.getPendingRequests(host)
            val jsonArray = JSONArray()
            for (req in requests) {
                val item = JSONObject().apply {
                    put("id", req.id)
                    put("endpoint", req.endpoint)
                    put("method", req.method)
                    put("payload", req.payloadJson)
                    put("createdAt", req.createdAt)
                }
                jsonArray.put(item)
            }
            jsonArray.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting queued requests", e)
            "[]"
        }
    }

    /**
     * Called by JS when an offline request has been successfully released/synced to the CBT server.
     */
    @JavascriptInterface
    fun notifySyncSuccess(host: String, requestId: String) {
        try {
            cacheManager.removePendingRequest(requestId)
            val remaining = cacheManager.getPendingRequests(host).size
            val statusMsg = if (remaining > 0) {
                "Menyinkronkan... Sisa queue: $remaining"
            } else {
                "✅ Seluruh cache IndexedDB berhasil dirilis ke server CBT!"
            }
            onSyncStatusChanged?.invoke(remaining > 0, statusMsg)
            Log.d(TAG, "Synced request $requestId successfully. Remaining: $remaining")
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying sync success", e)
        }
    }

    /**
     * Logs debug/status messages from JavaScript to Logcat & UI listener.
     */
    @JavascriptInterface
    fun logCacheEvent(tag: String, message: String) {
        Log.d("JS_$tag", message)
    }
}
