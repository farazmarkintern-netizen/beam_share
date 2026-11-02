package com.example.beamshare.documentscanner

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ScannedDocRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Read ──────────────────────────────────────────────────────────────────

    fun getAll(): List<ScannedDoc> {
        val json = prefs.getString(KEY_DOCS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    fun add(doc: ScannedDoc) {
        val list = getAll().toMutableList()
        list.add(0, doc)
        if (list.size > MAX_DOCS) list.subList(MAX_DOCS, list.size).clear()
        save(list)
    }

    fun rename(id: String, newName: String) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx != -1) {
            list[idx] = list[idx].copy(name = newName)
            save(list)
        }
    }

    fun delete(id: String) {
        val list = getAll().toMutableList()
        list.removeAll { it.id == id }
        save(list)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun save(list: List<ScannedDoc>) {
        val array = JSONArray()
        list.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_DOCS, array.toString()).apply()
    }

    private fun toJson(doc: ScannedDoc) = JSONObject().apply {
        put("id", doc.id)
        put("name", doc.name)
        put("filePath", doc.filePath)
        put("sizeBytes", doc.sizeBytes)
        put("dateStamp", doc.dateStamp)
    }

    private fun fromJson(o: JSONObject) = ScannedDoc(
        id        = o.optString("id", UUID.randomUUID().toString()),
        name      = o.optString("name", "Document"),
        filePath  = o.optString("filePath", ""),
        sizeBytes = o.optLong("sizeBytes", 0L),
        dateStamp = o.optString("dateStamp", dateStamp())
    )

    // ── Singleton ─────────────────────────────────────────────────────────────

    companion object {
        private const val PREFS_NAME = "beamshare_scanned_docs"
        private const val KEY_DOCS   = "docs"
        private const val MAX_DOCS   = 200

        @Volatile private var INSTANCE: ScannedDocRepository? = null

        fun getInstance(context: Context): ScannedDocRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScannedDocRepository(context.applicationContext).also { INSTANCE = it }
            }

        private val FMT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        fun dateStamp(): String = FMT.format(Date())

        fun friendlyDate(stamp: String): String {
            return try {
                val d = FMT.parse(stamp) ?: return stamp
                val todayCal = java.util.Calendar.getInstance()
                val docCal   = java.util.Calendar.getInstance().also { it.time = d }
                val today    = FMT.format(todayCal.time)
                todayCal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                val yesterday = FMT.format(todayCal.time)
                val docStr    = FMT.format(docCal.time)
                when (docStr) {
                    today     -> "Today"
                    yesterday -> "Yesterday"
                    else      -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(d)
                }
            } catch (_: Exception) { stamp }
        }

        fun formatSize(bytes: Long): String = when {
            bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024      -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else               -> "$bytes B"
        }

        fun newId(): String = UUID.randomUUID().toString()
    }
}