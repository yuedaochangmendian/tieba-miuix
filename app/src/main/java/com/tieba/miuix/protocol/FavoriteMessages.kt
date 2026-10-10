// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.tieba.miuix.protocol

import org.json.JSONArray
import org.json.JSONObject

/**
 * 「我的收藏」列表项（`POST /c/f/post/threadstore` 的 store_thread 数组）。
 *
 * 服务端返回的全是字符串字段，这里统一转成数值/布尔，UI 层不用再关心形态。
 */
data class StoredThread(
    val threadId: Long,
    val title: String,
    val forumName: String,
    val authorName: String,
    val lastTime: Long,
    val replyCount: Int,
    val isDeleted: Boolean,
) {
    val displayTitle: String get() = title.ifBlank { "（无标题）" }

    companion object {
        fun parse(json: JSONObject): StoredThread? {
            val tid = json.optLongOrString("thread_id")
            if (tid <= 0L) return null
            val author = json.optJSONObject("author")
            val authorName = author?.let {
                it.optString("name_show").ifBlank { it.optString("name") }
            }.orEmpty()
            return StoredThread(
                threadId = tid,
                title = json.optString("title"),
                forumName = json.optString("forum_name"),
                authorName = authorName,
                lastTime = json.optLongOrString("last_time"),
                replyCount = json.optIntFromString("post_no"),
                isDeleted = json.opt("is_deleted").toBooleanFlag(),
            )
        }
    }
}

/** 收藏帖分页结果；服务端只给一个数组，是否还有下一页靠「这一页是否装满」推断。 */
data class StoredThreads(
    val items: List<StoredThread> = emptyList(),
    val errorCode: Int = 0,
    val errorMessage: String = "",
) {
    val hasMore: Boolean get() = items.size >= PAGE_SIZE

    companion object {
        const val PAGE_SIZE = 30

        fun parse(json: JSONObject): StoredThreads {
            val code = json.optInt("error_code", json.optJSONObject("error")?.optInt("errno") ?: 0)
            val message = json.optString("error_msg").ifBlank {
                json.optJSONObject("error")?.optString("errmsg").orEmpty()
            }
            val array = json.optJSONArray("store_thread") ?: JSONArray()
            val items = ArrayList<StoredThread>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                StoredThread.parse(item)?.let { items.add(it) }
            }
            return StoredThreads(items = items, errorCode = code, errorMessage = message)
        }
    }
}

/** 我关注的吧（`POST /c/f/forum/getforumlist` 的 forum_list 数组）。 */
data class ConcernedForum(
    val id: Long,
    val name: String,
    val level: Int,
    val isSigned: Boolean,
) {
    companion object {
        fun parse(json: JSONObject): ConcernedForum? {
            val name = json.optString("forum_name")
            if (name.isBlank()) return null
            return ConcernedForum(
                id = json.optLongOrString("forum_id"),
                name = name,
                level = json.optIntFromString("level_id"),
                isSigned = json.optIntFromString("is_sign") != 0,
            )
        }
    }
}

/** 我关注的吧列表结果。 */
data class ConcernedForums(
    val items: List<ConcernedForum> = emptyList(),
    val errorCode: Int = 0,
    val errorMessage: String = "",
) {
    companion object {
        fun parse(json: JSONObject): ConcernedForums {
            val code = json.optInt("error_code", json.optJSONObject("error")?.optInt("errno") ?: 0)
            val message = json.optString("error_msg").ifBlank {
                json.optJSONObject("error")?.optString("errmsg").orEmpty()
            }
            val array = json.optJSONArray("forum_list") ?: JSONArray()
            val items = ArrayList<ConcernedForum>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                ConcernedForum.parse(item)?.let { items.add(it) }
            }
            return ConcernedForums(items = items, errorCode = code, errorMessage = message)
        }
    }
}

/** 签到结果：单个吧签到的详情，或批量签到的汇总。 */
data class SignResult(
    val success: Boolean,
    val errorCode: Int,
    val errorMessage: String,
    val signedCount: Int = 0,
    val continuousDays: Int = 0,
    val rank: Int = 0,
    val bonusPoint: Int = 0,
    val levelName: String = "",
) {
    /** 面向用户的一句话说明。 */
    val displayMessage: String
        get() = when {
            success && continuousDays > 0 ->
                "签到成功：连续 " + continuousDays + " 天" +
                    (if (rank > 0) "，本吧第 " + rank + " 个签到" else "") +
                    (if (bonusPoint > 0) "，+" + bonusPoint + " 经验" else "")
            success -> "签到成功"
            errorCode == 340006 || errorCode == 4 -> "登录状态已失效，请到「设置 → 账号」重新登录"
            errorMessage.isNotBlank() -> errorMessage
            errorCode != 0 -> "签到失败（错误码 " + errorCode + "）"
            else -> "签到失败"
        }

    companion object {
        /** 单个吧签到（`/c/c/forum/sign`）。 */
        fun parseSingle(json: JSONObject): SignResult {
            val code = json.optInt("error_code")
            val message = json.optString("error_msg")
            val userInfo = json.optJSONObject("user_info")
            val signIn = userInfo?.optIntFromString("is_sign_in") ?: 0
            val levelName = userInfo?.optString("level_name").orEmpty()
            // is_sign_in=0 表示今天已经签过了，服务端仍算成功。
            val success = code == 0
            return SignResult(
                success = success,
                errorCode = code,
                errorMessage = if (success) "" else message.ifBlank { "签到失败" },
                signedCount = if (signIn >= 1 || success) 1 else 0,
                continuousDays = userInfo?.optIntFromString("cont_sign_num") ?: 0,
                rank = userInfo?.optIntFromString("user_sign_rank") ?: 0,
                bonusPoint = userInfo?.optIntFromString("sign_bonus_point") ?: 0,
                levelName = levelName,
            )
        }

        /** 批量签到（`/c/c/forum/msign`），`info` 里每个吧一条。 */
        fun parseBatch(json: JSONObject): SignResult {
            val code = json.optInt("error_code")
            val info = json.optJSONArray("info") ?: JSONArray()
            var signed = 0
            for (i in 0 until info.length()) {
                val item = info.optJSONObject(i) ?: continue
                if (item.optIntFromString("signed") != 0) signed++
            }
            val codes = json.optJSONObject("error")?.optIntFromString("errno") ?: code
            val message = json.optJSONObject("error")?.optString("errmsg").orEmpty()
                .ifBlank { json.optString("error_msg") }
            return SignResult(
                success = codes == 0,
                errorCode = codes,
                errorMessage = if (codes == 0) "" else message.ifBlank { "批量签到失败" },
                signedCount = signed,
            )
        }
    }
}

/** 关注 / 取关、收藏 / 取消收藏这类写操作的通用结果。 */
data class CommonResult(
    val success: Boolean,
    val errorCode: Int,
    val errorMessage: String,
) {
    /** 面向用户的一句话说明。 */
    val displayMessage: String
        get() = when {
            success -> "操作成功"
            errorCode == 340006 || errorCode == 4 -> "登录状态已失效，请到「设置 → 账号」重新登录"
            errorMessage.isNotBlank() -> errorMessage
            errorCode != 0 -> "操作失败（错误码 " + errorCode + "）"
            else -> "操作失败"
        }

    companion object {
        fun of(json: JSONObject): CommonResult {
            // 老通道用 error_code，新通道（/c/s/sync 一类）成功时只回 {"no":0}。
            val hasErrorCode = json.has("error_code")
            val code = json.optInt("error_code", json.optJSONObject("error")?.optInt("errno") ?: 0)
            val message = json.optString("error_msg").ifBlank {
                json.optJSONObject("error")?.optString("errmsg").orEmpty()
            }
            val success = if (hasErrorCode) code == 0 else json.optInt("no", 1) == 0
            return CommonResult(
                success = success,
                errorCode = if (success) 0 else code,
                errorMessage = if (success) "" else message,
            )
        }
    }
}
