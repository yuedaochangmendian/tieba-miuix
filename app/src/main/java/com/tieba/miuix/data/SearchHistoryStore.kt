// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.data

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

/**
 * 搜索历史。
 *
 * 与 [HistoryStore] 一样，只用 SharedPreferences + JSON，不引入 Room。
 * TiebaLite 用 Room 存搜索历史（`SearchDao`），这里用等价的内存 + JSON 方案替代。
 */
@Stable
class SearchHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var keywords by mutableStateOf(load())
        private set

    fun add(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        val next = (listOf(trimmed) + keywords.filterNot { it.equals(trimmed, ignoreCase = true) })
            .take(MAX_ITEMS)
        keywords = next
        save(next)
    }

    fun remove(keyword: String) {
        val next = keywords.filterNot { it == keyword }
        keywords = next
        save(next)
    }

    fun clear() {
        keywords = emptyList()
        prefs.edit().remove(KEY_KEYWORDS).apply()
    }

    private fun save(list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        prefs.edit().putString(KEY_KEYWORDS, array.toString()).apply()
    }

    private fun load(): List<String> = runCatching {
        val raw = prefs.getString(KEY_KEYWORDS, null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotEmpty()) add(value)
            }
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val PREFS_NAME = "tieba_miuix_search_history"
        private const val KEY_KEYWORDS = "keywords"
        private const val MAX_ITEMS = 50
    }
}
