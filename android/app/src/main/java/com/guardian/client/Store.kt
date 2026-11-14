package com.guardian.client

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * On-device store. Everything the app collects lives here until it has been
 * emailed out in a report. Timestamps are epoch milliseconds (UTC).
 *
 * UNIQUE constraints make every insert idempotent, so re-scanning the call log
 * or SMS inbox never produces duplicates.
 */
class Store(ctx: Context) : SQLiteOpenHelper(ctx, "guardian.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE calls(
                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                 ts INTEGER, number TEXT, name TEXT, type TEXT, duration INTEGER,
                 UNIQUE(number, ts, duration))"""
        )
        db.execSQL(
            """CREATE TABLE sms(
                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                 ts INTEGER, number TEXT, direction TEXT, body TEXT,
                 UNIQUE(number, ts, direction))"""
        )
        db.execSQL(
            """CREATE TABLE notifs(
                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                 ts INTEGER, pkg TEXT, app TEXT, title TEXT, text TEXT,
                 UNIQUE(pkg, ts, title, text))"""
        )
        db.execSQL(
            """CREATE TABLE locs(
                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                 ts INTEGER, lat REAL, lon REAL, acc REAL,
                 UNIQUE(ts))"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}

    // ---- inserts (idempotent) --------------------------------------------
    fun addCall(ts: Long, number: String?, name: String?, type: String, dur: Long) =
        insert("calls", ContentValues().apply {
            put("ts", ts); put("number", number); put("name", name)
            put("type", type); put("duration", dur)
        })

    fun addSms(ts: Long, number: String?, direction: String, body: String?) =
        insert("sms", ContentValues().apply {
            put("ts", ts); put("number", number); put("direction", direction); put("body", body)
        })

    fun addNotif(ts: Long, pkg: String, app: String?, title: String?, text: String?) =
        insert("notifs", ContentValues().apply {
            put("ts", ts); put("pkg", pkg); put("app", app); put("title", title); put("text", text)
        })

    fun addLoc(ts: Long, lat: Double, lon: Double, acc: Float) =
        insert("locs", ContentValues().apply {
            put("ts", ts); put("lat", lat); put("lon", lon); put("acc", acc)
        })

    private fun insert(table: String, v: ContentValues) {
        writableDatabase.insertWithOnConflict(table, null, v, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Rows in [from, to) for a table, ordered by time. */
    fun window(table: String, from: Long, to: Long): List<Map<String, String>> {
        val out = ArrayList<Map<String, String>>()
        readableDatabase.rawQuery(
            "SELECT * FROM $table WHERE ts>=? AND ts<? ORDER BY ts",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            val cols = c.columnNames
            while (c.moveToNext()) {
                val row = HashMap<String, String>()
                for (i in cols.indices) row[cols[i]] = c.getString(i) ?: ""
                out.add(row)
            }
        }
        return out
    }

    /** Delete rows older than [cutoff] across all tables (retention cleanup). */
    fun prune(cutoff: Long) {
        for (t in listOf("calls", "sms", "notifs", "locs"))
            writableDatabase.delete(t, "ts < ?", arrayOf(cutoff.toString()))
    }
}
