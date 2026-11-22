package com.example.safepath_test1.session

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal data class QueuedLocation(val id: Long, val latitude: Double, val longitude: Double, val accuracy: Double?, val capturedAt: Long)

internal class LocationQueue(context: Context) : SQLiteOpenHelper(context, "journey_locations.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE pending (id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT NOT NULL, channel TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, accuracy REAL, captured_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX pending_session ON pending(uid, channel, id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun add(uid: String, channel: String, location: android.location.Location) {
        val values = ContentValues().apply {
            put("uid", uid)
            put("channel", channel)
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            if (location.hasAccuracy()) put("accuracy", location.accuracy.toDouble()) else putNull("accuracy")
            put("captured_at", location.time)
        }
        writableDatabase.insertOrThrow("pending", null, values)
    }

    fun first(uid: String, channel: String): QueuedLocation? =
        readableDatabase.query("pending", arrayOf("id", "latitude", "longitude", "accuracy", "captured_at"),
            "uid=? AND channel=?", arrayOf(uid, channel), null, null, "id ASC", "1").use {
            if (!it.moveToFirst()) null else QueuedLocation(it.getLong(0), it.getDouble(1), it.getDouble(2), if (it.isNull(3)) null else it.getDouble(3), it.getLong(4))
        }

    fun remove(uid: String, channel: String, id: Long) {
        writableDatabase.delete("pending", "uid=? AND channel=? AND id=?", arrayOf(uid, channel, id.toString()))
    }

    fun clear(uid: String, channel: String) {
        writableDatabase.delete("pending", "uid=? AND channel=?", arrayOf(uid, channel))
    }
}
