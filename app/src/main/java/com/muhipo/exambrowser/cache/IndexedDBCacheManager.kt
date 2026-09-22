package com.muhipo.exambrowser.cache

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native Persistent Offline Cache Manager for CBT Exam Browser.
 * Safely stores IndexedDB snapshots, student answers, and offline request queues in SQLite
 * so that no data is lost even if the app is closed, device restarts, or connection drops.
 */
class IndexedDBCacheManager private constructor(context: Context) {

    private val dbHelper = DatabaseHelper(context.applicationContext)

    companion object {
        @Volatile
        private var instance: IndexedDBCacheManager? = null

        fun getInstance(context: Context): IndexedDBCacheManager {
            return instance ?: synchronized(this) {
                instance ?: IndexedDBCacheManager(context).also { instance = it }
            }
        }

        private const val DB_NAME = "exambro_cbt_cache.db"
        private const val DB_VERSION = 1

        private const val TABLE_ANSWERS = "cbt_answers"
        private const val TABLE_INDEXEDDB = "cbt_indexeddb"
        private const val TABLE_OFFLINE_QUEUE = "cbt_offline_queue"
    }

    private class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            // Table for student CBT answer snapshots (key-value per host/URL)
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_ANSWERS (
                    host TEXT NOT NULL,
                    question_id TEXT NOT NULL,
                    answer_json TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (host, question_id)
                )
                """.trimIndent()
            )

            // Table for general IndexedDB key-value store snapshots per host
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_INDEXEDDB (
                    host TEXT NOT NULL,
                    store_name TEXT NOT NULL,
                    key_id TEXT NOT NULL,
                    value_json TEXT NOT NULL,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (host, store_name, key_id)
                )
                """.trimIndent()
            )

            // Table for queued HTTP POST/PUT answer submissions during server downtime
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_OFFLINE_QUEUE (
                    id TEXT PRIMARY KEY,
                    host TEXT NOT NULL,
                    endpoint TEXT NOT NULL,
                    method TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_ANSWERS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_INDEXEDDB")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_OFFLINE_QUEUE")
            onCreate(db)
        }
    }

    data class OfflineRequest(
        val id: String,
        val host: String,
        val endpoint: String,
        val method: String,
        val payloadJson: String,
        val createdAt: Long
    )

    data class CacheStats(
        val totalAnswers: Int,
        val totalIndexedDbEntries: Int,
        val pendingOfflineRequests: Int,
        val lastUpdated: Long
    )

    /**
     * Saves or updates a student's answer for a CBT question.
     */
    fun saveAnswer(host: String, questionId: String, answerJson: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("host", host.lowercase().trim())
            put("question_id", questionId.trim())
            put("answer_json", answerJson)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_ANSWERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Retrieves all cached answers for a given CBT host.
     */
    fun getAllAnswers(host: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_ANSWERS,
            arrayOf("question_id", "answer_json"),
            "host = ?",
            arrayOf(host.lowercase().trim()),
            null, null, null
        )
        cursor.use {
            while (it.moveToNext()) {
                val qId = it.getString(0)
                val json = it.getString(1)
                map[qId] = json
            }
        }
        return map
    }

    /**
     * Saves general IndexedDB store data into SQLite native persistence.
     */
    fun saveIndexedDbEntry(host: String, storeName: String, key: String, valueJson: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("host", host.lowercase().trim())
            put("store_name", storeName.trim())
            put("key_id", key.trim())
            put("value_json", valueJson)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_INDEXEDDB, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Gets an IndexedDB entry by host, store, and key.
     */
    fun getIndexedDbEntry(host: String, storeName: String, key: String): String? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_INDEXEDDB,
            arrayOf("value_json"),
            "host = ? AND store_name = ? AND key_id = ?",
            arrayOf(host.lowercase().trim(), storeName.trim(), key.trim()),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }

    /**
     * Gets all entries for an IndexedDB store on a host.
     */
    fun getAllIndexedDbEntries(host: String, storeName: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_INDEXEDDB,
            arrayOf("key_id", "value_json"),
            "host = ? AND store_name = ?",
            arrayOf(host.lowercase().trim(), storeName.trim()),
            null, null, null
        )
        cursor.use {
            while (it.moveToNext()) {
                map[it.getString(0)] = it.getString(1)
            }
        }
        return map
    }

    /**
     * Queues an offline request (submission payload) when server/connection is down.
     */
    fun queueOfflineRequest(host: String, endpoint: String, method: String, payloadJson: String): String {
        val requestId = "req_" + System.currentTimeMillis() + "_" + (1000..9999).random()
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("id", requestId)
            put("host", host.lowercase().trim())
            put("endpoint", endpoint.trim())
            put("method", method.uppercase().trim())
            put("payload_json", payloadJson)
            put("created_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_OFFLINE_QUEUE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return requestId
    }

    /**
     * Retrieves all pending offline requests for a host.
     */
    fun getPendingRequests(host: String): List<OfflineRequest> {
        val list = mutableListOf<OfflineRequest>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_OFFLINE_QUEUE,
            arrayOf("id", "host", "endpoint", "method", "payload_json", "created_at"),
            "host = ?",
            arrayOf(host.lowercase().trim()),
            null, null, "created_at ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    OfflineRequest(
                        id = it.getString(0),
                        host = it.getString(1),
                        endpoint = it.getString(2),
                        method = it.getString(3),
                        payloadJson = it.getString(4),
                        createdAt = it.getLong(5)
                    )
                )
            }
        }
        return list
    }

    /**
     * Removes a successfully synced offline request from the queue.
     */
    fun removePendingRequest(requestId: String) {
        val db = dbHelper.writableDatabase
        db.delete(TABLE_OFFLINE_QUEUE, "id = ?", arrayOf(requestId))
    }

    /**
     * Clears pending queue for a host after full sync.
     */
    fun clearPendingQueueForHost(host: String) {
        val db = dbHelper.writableDatabase
        db.delete(TABLE_OFFLINE_QUEUE, "host = ?", arrayOf(host.lowercase().trim()))
    }

    /**
     * Returns statistics about cached data for a CBT host.
     */
    fun getCacheStats(host: String): CacheStats {
        val db = dbHelper.readableDatabase
        val cleanHost = host.lowercase().trim()

        var answersCount = 0
        var indexedDbCount = 0
        var pendingQueueCount = 0
        var maxTimestamp = 0L

        db.rawQuery("SELECT COUNT(*), MAX(updated_at) FROM $TABLE_ANSWERS WHERE host = ?", arrayOf(cleanHost)).use {
            if (it.moveToFirst()) {
                answersCount = it.getInt(0)
                maxTimestamp = maxOf(maxTimestamp, it.getLong(1))
            }
        }

        db.rawQuery("SELECT COUNT(*), MAX(updated_at) FROM $TABLE_INDEXEDDB WHERE host = ?", arrayOf(cleanHost)).use {
            if (it.moveToFirst()) {
                indexedDbCount = it.getInt(0)
                maxTimestamp = maxOf(maxTimestamp, it.getLong(1))
            }
        }

        db.rawQuery("SELECT COUNT(*) FROM $TABLE_OFFLINE_QUEUE WHERE host = ?", arrayOf(cleanHost)).use {
            if (it.moveToFirst()) {
                pendingQueueCount = it.getInt(0)
            }
        }

        return CacheStats(
            totalAnswers = answersCount,
            totalIndexedDbEntries = indexedDbCount,
            pendingOfflineRequests = pendingQueueCount,
            lastUpdated = maxTimestamp
        )
    }

    /**
     * Gets a total summary across all CBT hosts.
     */
    fun getTotalCacheSummary(): JSONObject {
        val json = JSONObject()
        val db = dbHelper.readableDatabase

        var totalAnswers = 0
        var totalIndexedDb = 0
        var totalPending = 0

        db.rawQuery("SELECT COUNT(*) FROM $TABLE_ANSWERS", null).use {
            if (it.moveToFirst()) totalAnswers = it.getInt(0)
        }
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_INDEXEDDB", null).use {
            if (it.moveToFirst()) totalIndexedDb = it.getInt(0)
        }
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_OFFLINE_QUEUE", null).use {
            if (it.moveToFirst()) totalPending = it.getInt(0)
        }

        json.put("total_answers", totalAnswers)
        json.put("total_indexeddb", totalIndexedDb)
        json.put("total_pending_queue", totalPending)
        return json
    }

    /**
     * Completely purges cache for a specific CBT host.
     */
    fun clearCacheForHost(host: String) {
        val db = dbHelper.writableDatabase
        val cleanHost = host.lowercase().trim()
        db.delete(TABLE_ANSWERS, "host = ?", arrayOf(cleanHost))
        db.delete(TABLE_INDEXEDDB, "host = ?", arrayOf(cleanHost))
        db.delete(TABLE_OFFLINE_QUEUE, "host = ?", arrayOf(cleanHost))
    }

    /**
     * Purges all cache across all hosts (Admin Action).
     */
    fun clearAllCache() {
        val db = dbHelper.writableDatabase
        db.delete(TABLE_ANSWERS, null, null)
        db.delete(TABLE_INDEXEDDB, null, null)
        db.delete(TABLE_OFFLINE_QUEUE, null, null)
    }
}
