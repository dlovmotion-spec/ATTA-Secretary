package com.attaproductions.secretary

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TaskDb(context: Context) : SQLiteOpenHelper(context, "atta_secretary.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE tasks(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            notes TEXT NOT NULL DEFAULT '',
            dueAt INTEGER NOT NULL,
            priority INTEGER NOT NULL DEFAULT 1,
            repeatRule TEXT NOT NULL DEFAULT 'none',
            done INTEGER NOT NULL DEFAULT 0
        )""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun add(title: String, notes: String, dueAt: Long, priority: Int, repeat: String): Long {
        val v = ContentValues().apply {
            put("title", title); put("notes", notes); put("dueAt", dueAt)
            put("priority", priority); put("repeatRule", repeat); put("done", 0)
        }
        return writableDatabase.insert("tasks", null, v)
    }

    fun tasks(showDone: Boolean = false): List<Task> {
        val out = mutableListOf<Task>()
        val where = if (showDone) null else "done=0"
        readableDatabase.query("tasks", null, where, null, null, null, "done ASC, dueAt ASC").use { c ->
            while (c.moveToNext()) out += Task(
                c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("title")),
                c.getString(c.getColumnIndexOrThrow("notes")),
                c.getLong(c.getColumnIndexOrThrow("dueAt")),
                c.getInt(c.getColumnIndexOrThrow("priority")),
                c.getString(c.getColumnIndexOrThrow("repeatRule")),
                c.getInt(c.getColumnIndexOrThrow("done")) == 1
            )
        }
        return out
    }

    fun markDone(id: Long) {
        writableDatabase.update("tasks", ContentValues().apply { put("done", 1) }, "id=?", arrayOf(id.toString()))
    }

    fun delete(id: Long) { writableDatabase.delete("tasks", "id=?", arrayOf(id.toString())) }
}
