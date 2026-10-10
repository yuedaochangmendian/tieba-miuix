// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.data

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tieba.miuix.protocol.toPlainText
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * 屏蔽规则（黑名单 / 白名单），移植自 TiebaLite 的 `Block` + `BlockManager`。
 *
 * 规则分两类：
 * - [TYPE_KEYWORD]：按关键词命中正文标题或内容；[isRegex] 为真时按正则匹配。
 * - [TYPE_USER]：按用户 id 或用户名命中作者。
 *
 * 白名单优先于黑名单：命中白名单的内容一律不屏蔽（TiebaLite 同一套语义）。
 */
data class BlockRule(
    val id: Long = 0L,
    val category: Int = CATEGORY_BLACK_LIST,
    val type: Int = TYPE_KEYWORD,
    /** 关键词本身（单条一条规则一个关键词，和 TiebaLite 的 JSON 数组等价）。 */
    val keyword: String = "",
    val userName: String = "",
    val uid: Long = 0L,
    val isRegex: Boolean = false,
) {
    val displayText: String
        get() = when (type) {
            TYPE_USER -> userName.ifEmpty { uid.toString() }
            else -> keyword
        }

    fun writeTo(json: JSONObject) {
        json.put("id", id)
        json.put("category", category)
        json.put("type", type)
        json.put("keyword", keyword)
        json.put("user_name", userName)
        json.put("uid", uid)
        json.put("is_regex", isRegex)
    }

    companion object {
        const val CATEGORY_BLACK_LIST = 10
        const val CATEGORY_WHITE_LIST = 11
        const val TYPE_KEYWORD = 0
        const val TYPE_USER = 1

        fun parse(json: JSONObject): BlockRule = BlockRule(
            id = json.optLong("id"),
            category = json.optInt("category", CATEGORY_BLACK_LIST),
            type = json.optInt("type", TYPE_KEYWORD),
            keyword = json.optString("keyword"),
            userName = json.optString("user_name"),
            uid = json.optLong("uid"),
            isRegex = json.optBoolean("is_regex", false),
        )
    }
}

/**
 * 屏蔽规则的本地存储和匹配器。
 *
 * 判定逻辑与 TiebaLite `BlockManager.shouldBlock` 对齐：白名单先命中就直接放行，
 * 否则再看黑名单。正则写错（编译失败）时按「不命中所属规则」处理，不让一条坏规则屏蔽全部内容。
 */
@Stable
class BlockStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var rules by mutableStateOf(load())
        private set

    val blackList: List<BlockRule>
        get() = rules.filter { it.category == BlockRule.CATEGORY_BLACK_LIST }

    val whiteList: List<BlockRule>
        get() = rules.filter { it.category == BlockRule.CATEGORY_WHITE_LIST }

    val isEmpty: Boolean get() = rules.isEmpty()

    /** 屏蔽正文：白名单任一命中即放行，否则黑名单任一命中即屏蔽。 */
    fun shouldBlock(content: String): Boolean {
        if (content.isBlank()) return false
        if (whiteList.any { it.matchesContent(content) }) return false
        return blackList.any { it.matchesContent(content) }
    }

    /** 屏蔽作者：白名单先放行。 */
    fun shouldBlockUser(uid: Long, userName: String?): Boolean {
        if (whiteList.any { it.matchesUser(uid, userName) }) return false
        return blackList.any { it.matchesUser(uid, userName) }
    }

    /**
     * 帖子是否该被过滤掉：作者命中用户规则，或标题 / 首楼正文命中关键词规则。
     *
     * 列表页用它整条过滤；帖子详情页不整条丢，而是折叠成一句提示（见 `ThreadDetailPage`）。
     */
    fun shouldBlockThread(thread: com.tieba.miuix.protocol.ThreadInfo): Boolean {
        val uid = thread.authorId.takeIf { it > 0L } ?: thread.author?.id ?: 0L
        if (shouldBlockUser(uid, thread.author?.displayName)) return true
        val content = buildString {
            append(thread.title)
            append('\n')
            append(thread.firstPostContent.toPlainText())
        }
        return shouldBlock(content)
    }

    /** 楼层是否该被折叠：作者命中用户规则，或正文命中关键词规则。 */
    fun shouldBlockPost(post: com.tieba.miuix.protocol.Post): Boolean {
        val uid = post.authorId.takeIf { it > 0L } ?: post.author?.id ?: 0L
        if (shouldBlockUser(uid, post.author?.displayName)) return true
        return shouldBlock(post.plainText)
    }

    fun add(rule: BlockRule) {
        val id = nextId()
        rules = rules + rule.copy(id = id)
        save()
    }

    fun remove(id: Long) {
        rules = rules.filterNot { it.id == id }
        save()
    }

    fun clear() {
        rules = emptyList()
        save()
    }

    /** 添加一条关键词规则；同类型同关键词已存在时不重复添加。 */
    fun addKeyword(keyword: String, category: Int = BlockRule.CATEGORY_BLACK_LIST, isRegex: Boolean = false) {
        val text = keyword.trim()
        if (text.isEmpty()) return
        if (rules.any { it.type == BlockRule.TYPE_KEYWORD && it.keyword == text && it.category == category }) return
        add(BlockRule(category = category, type = BlockRule.TYPE_KEYWORD, keyword = text, isRegex = isRegex))
    }

    /** 添加一条用户规则；uid 为 0 且用户名为空时忽略。 */
    fun addUser(
        uid: Long,
        userName: String,
        category: Int = BlockRule.CATEGORY_BLACK_LIST,
    ) {
        val name = userName.trim()
        if (uid <= 0L && name.isEmpty()) return
        if (rules.any { it.type == BlockRule.TYPE_USER && it.uid == uid && it.userName == name }) return
        add(BlockRule(category = category, type = BlockRule.TYPE_USER, userName = name, uid = uid))
    }

    private fun nextId(): Long = (rules.maxOfOrNull { it.id } ?: 0L) + 1L

    private fun BlockRule.matchesContent(content: String): Boolean {
        if (type != BlockRule.TYPE_KEYWORD || keyword.isEmpty()) return false
        return if (isRegex) {
            runCatching { Pattern.compile(keyword).matcher(content).find() }.getOrDefault(false)
        } else {
            content.contains(keyword)
        }
    }

    private fun BlockRule.matchesUser(uid: Long, userName: String?): Boolean {
        if (type != BlockRule.TYPE_USER) return false
        if (this.uid > 0L && this.uid == uid) return true
        return userName != null && this.userName.isNotEmpty() && this.userName == userName
    }

    private fun load(): List<BlockRule> {
        val raw = prefs.getString(KEY_RULES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(BlockRule.parse(item))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save() {
        val array = JSONArray()
        rules.forEach { rule ->
            val json = JSONObject()
            rule.writeTo(json)
            array.put(json)
        }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
    }

    companion object {
        const val PREFS_NAME = "tieba_miuix_block"
        private const val KEY_RULES = "rules"
    }
}
