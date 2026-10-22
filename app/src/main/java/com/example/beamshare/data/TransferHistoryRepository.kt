package com.example.beamshare.data

import android.content.Context
import android.content.SharedPreferences
import com.example.beamshare.model.HistoryStatus
import com.example.beamshare.model.TransferDirection
import com.example.beamshare.model.TransferHistoryItem
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Persists transfer history using SharedPreferences + JSON.
 */
class TransferHistoryRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Read ──────────────────────────────────────────────────────────────────

    fun getAllItems(): List<TransferHistoryItem> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i -> fromJson(array.getJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    fun addItem(item: TransferHistoryItem) {
        val current = getAllItems().toMutableList()
        current.add(0, item)          // newest first
        if (current.size > MAX_ITEMS) {
            current.subList(MAX_ITEMS, current.size).clear()
        }
        save(current)
    }

    fun deleteByIds(ids: Set<String>) {
        val current = getAllItems().toMutableList()
        current.removeAll { it.id in ids }
        save(current)
    }

    fun clearAll() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun save(items: List<TransferHistoryItem>) {
        val array = JSONArray()
        items.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    private fun toJson(item: TransferHistoryItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("direction", item.direction.name)
        put("deviceName", item.deviceName)
        put("totalSize", item.totalSize)
        put("fileCount", item.fileCount)
        put("status", item.status.name)
        put("dateTime", item.dateTime)
        put("speed", item.speed ?: "")
        put("duration", item.duration ?: "")
        put("method", item.method ?: "")
        put("fileType", item.fileType)
        put("filePath", item.filePath ?: "")
    }

    private fun fromJson(o: JSONObject): TransferHistoryItem = TransferHistoryItem(
        id = o.optString("id", UUID.randomUUID().toString()),
        title = o.optString("title", "Unknown"),
        direction = try { TransferDirection.valueOf(o.optString("direction", "SENT")) }
        catch (_: Exception) { TransferDirection.SENT },
        deviceName = o.optString("deviceName", "Unknown device"),
        totalSize = o.optString("totalSize", "—"),
        fileCount = o.optInt("fileCount", 1),
        status = try { HistoryStatus.valueOf(o.optString("status", "DONE")) }
        catch (_: Exception) { HistoryStatus.DONE },
        dateTime = o.optString("dateTime", "Today"),
        speed = o.optString("speed", "").takeIf { it.isNotBlank() },
        duration = o.optString("duration", "").takeIf { it.isNotBlank() },
        method = o.optString("method", "").takeIf { it.isNotBlank() },
        fileType = o.optString("fileType", "mixed"),
        filePath = o.optString("filePath", "").takeIf { it.isNotBlank() }
    )

    // ── Singleton + date helpers ──────────────────────────────────────────────

    companion object {
        private const val PREFS_NAME  = "beamshare_history"
        private const val KEY_HISTORY = "history_items"
        private const val MAX_ITEMS   = 200

        @Volatile
        private var INSTANCE: TransferHistoryRepository? = null

        fun getInstance(context: Context): TransferHistoryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TransferHistoryRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        private val DAY_FMT   = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        private val TODAY_FMT = SimpleDateFormat("yyyy-MM-dd",  Locale.getDefault())

        fun todayLabel(): String = "Today"

        fun friendlyDate(isoDate: String): String {
            return try {
                val stored    = TODAY_FMT.parse(isoDate) ?: return isoDate
                val todayCal  = java.util.Calendar.getInstance()
                val storedCal = java.util.Calendar.getInstance().also { it.time = stored }

                val todayStr   = TODAY_FMT.format(todayCal.time)
                val storedStr  = TODAY_FMT.format(storedCal.time)

                todayCal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                val yesterdayStr = TODAY_FMT.format(todayCal.time)

                when (storedStr) {
                    todayStr     -> "Today"
                    yesterdayStr -> "Yesterday"
                    else         -> DAY_FMT.format(stored)
                }
            } catch (_: Exception) {
                isoDate
            }
        }

        fun dateStamp(): String = TODAY_FMT.format(Date())
    }
}