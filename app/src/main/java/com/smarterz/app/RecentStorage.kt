package com.smarterz.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RecentStorage(context: Context) {

    private val prefs = context.getSharedPreferences("smarterz_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "recent_items_v4"
    private val maxItems = 10

    fun getAll(): List<MediaItem> {
        val json = prefs.getString(key, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<MediaItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(item: MediaItem) {
        val items = getAll()
            .filter { !(it.id == item.id && it.type == item.type) }
            .toMutableList()
        items.add(0, item)
        if (items.size > maxItems) items.removeAt(items.size - 1)
        save(items)
    }

    fun remove(id: Int, type: String) {
        val items = getAll().filter { !(it.id == id && it.type == type) }
        save(items)
    }

    private fun save(items: List<MediaItem>) {
        prefs.edit().putString(key, gson.toJson(items)).apply()
    }
}
