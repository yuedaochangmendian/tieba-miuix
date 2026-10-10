// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.data

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tieba.miuix.protocol.ThreadInfo
import org.json.JSONArray
import org.json.JSONObject

/** 一条浏览记录中的帖子。 */
data class ThreadHistory(
    val tid: Long,
    val title: String,
    val forumName: String,
    val authorName: String,
    val replyNum: Int,
    val time: Long,
)

/** 最近逛过的吧。 */
data class ForumHistory(
    val name: String,
    val time: Long,
)

/** 把接口返回的帖子转成浏览记录。 */
fun ThreadInfo.toHistory(forumName: String, now: Long = System.currentTimeMillis()): ThreadHistory =
    ThreadHistory(
        tid = id,
        title = title,
        forumName = fname.ifEmpty { forumName },
        authorName = authorName,
        replyNum = replyNum,
        time = now,
    )

/**
 * 浏览记录。
 *
 * 只用 SharedPreferences + JSON 存储，不引入 Room：记录量小（各上限 200 条），
 * JSON 编解码在 IO 线程执行即可，代价远小于多一个注解处理器的复杂度。
 */
@Stable
class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var threads by mutableStateOf(loadThreads())
        private set

    var forums by mutableStateOf(loadForums())
        private set

    /** 隐身模式下由调用方跳过写入；这里只负责存储本身。 */
    fun addThread(record: ThreadHistory) {
        if (record.tid == 0L) return
        val kept = threads.filterNot { it.tid == record.tid }
        val next = (listOf(record) + kept).take(MAX_THREADS)
        threads = next
        prefs.edit().putString(KEY_THREADS, threadsToJson(next).toString()).apply()
    }

    fun addForum(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        val kept = forums.filterNot { it.name == trimmed }
        val next = (listOf(ForumHistory(trimmed, now)) + kept).take(MAX_FORUMS)
        forums = next
        prefs.edit().putString(KEY_FORUMS, forumsToJson(next).toString()).apply()
    }

    fun removeThread(tid: Long) {
        val next = threads.filterNot { it.tid == tid }
        threads = next
        prefs.edit().putString(KEY_THREADS, threadsToJson(next).toString()).apply()
    }

    fun clearThreads() {
        threads = emptyList()
        prefs.edit().remove(KEY_THREADS).apply()
    }

    fun clearForums() {
        forums = emptyList()
        prefs.edit().remove(KEY_FORUMS).apply()
    }

    fun clearAll() {
        clearThreads()
        clearForums()
    }

    private fun loadThreads(): List<ThreadHistory> = runCatching {
        val raw = prefs.getString(KEY_THREADS, null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val tid = item.optLong("tid")
                if (tid == 0L) continue
                add(
                    ThreadHistory(
                        tid = tid,
                        title = item.optString("title"),
                        forumName = item.optString("forum"),
                        authorName = item.optString("author"),
                        replyNum = item.optInt("reply"),
                        time = item.optLong("time"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun loadForums(): List<ForumHistory> = runCatching {
        val raw = prefs.getString(KEY_FORUMS, null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val name = item.optString("name")
                if (name.isEmpty()) continue
                add(ForumHistory(name = name, time = item.optLong("time")))
            }
        }
    }.getOrDefault(emptyList())

    private fun threadsToJson(list: List<ThreadHistory>): JSONArray {
        val array = JSONArray()
        list.forEach { record ->
            array.put(
                JSONObject()
                    .put("tid", record.tid)
                    .put("title", record.title)
                    .put("forum", record.forumName)
                    .put("author", record.authorName)
                    .put("reply", record.replyNum)
                    .put("time", record.time),
            )
        }
        return array
    }

    private fun forumsToJson(list: List<ForumHistory>): JSONArray {
        val array = JSONArray()
        list.forEach { record ->
            array.put(JSONObject().put("name", record.name).put("time", record.time))
        }
        return array
    }

    companion object {
        private const val PREFS_NAME = "tieba_miuix_history"
        private const val KEY_THREADS = "threads"
        private const val KEY_FORUMS = "forums"
        private const val MAX_THREADS = 200
        private const val MAX_FORUMS = 30
    }
}
