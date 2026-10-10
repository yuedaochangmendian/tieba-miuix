// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

import org.json.JSONObject

/**
 * 用户名片（网页端 `GET /c/u/pc/userCard`）。
 *
 * 这是**匿名可用**的用户主页接口：只要拿到 `portrait` 就能查到昵称、吧龄、
 * 获赞/粉丝/关注数、个性签名与 IP 归属地，不需要登录。
 *
 * 响应形态：
 * ```
 * { "error_code": 0, "data": { "user_info": { "name_show": "...", "portrait": "...?t=..." } } }
 * ```
 */
data class UserCard(
    val portrait: String = "",
    val nameShow: String = "",
    /**
     * 贴吧内部 uid，用来查这个人的发言。
     *
     * 注意：实测 `userCard` 响应里**没有** `user_id` / `tieba_uid` 字段，
     * 所以这里基本是 0；调用方应从搜索接口或楼层数据里把 uid 一起带进来。
     * 保留字段是为了服务端哪天补上时能自动用起来。
     */
    val tiebaUid: Long = 0,
    val sex: Int = 0,
    val tbAge: String = "",
    val agreeNum: Int = 0,
    val fansNum: Int = 0,
    val concernNum: Int = 0,
    val intro: String = "",
    val ipAddress: String = "",
    val hasConcerned: Boolean = false,
) {
    /** 展示名，取不到昵称时退化成 portrait。 */
    val displayName: String get() = nameShow.ifEmpty { portrait }

    /**
     * 头像地址。
     *
     * 接口返回的 `portrait` 尾巴上挂着 `?t=1791602010`（缓存时间戳），
     * 直接拼进头像 URL 会变成两个问号，所以这里先切掉查询串。
     */
    val portraitUrl: String
        get() {
            val clean = portrait.substringBefore('?')
            return when {
                clean.isEmpty() -> ""
                clean.startsWith("http", ignoreCase = true) -> clean
                else -> "https://tb.himg.baidu.com/sys/portrait/item/" + clean
            }
        }

    /** 身份标识：切掉 URL 前缀与查询串后的裸串，接口只认这个形态。 */
    val barePortrait: String
        get() {
            val clean = portrait.substringBefore('?')
            val index = clean.lastIndexOf("/item/")
            return if (index >= 0) clean.substring(index + "/item/".length) else clean
        }

    /** 性别文案，0 未知 / 1 男 / 2 女。 */
    val genderLabel: String
        get() = when (sex) {
            1 -> "男"
            2 -> "女"
            else -> ""
        }

    companion object {
        /** 按 key 取数字，兼容字符串与数字两种形态。 */
        private fun JSONObject.intOf(key: String): Int = when (val raw = opt(key)) {
            null, JSONObject.NULL -> 0
            is Number -> raw.toInt()
            else -> parseCount(raw.toString())
        }

        fun parse(json: JSONObject): UserCard? {
            val info = json.optJSONObject("data")?.optJSONObject("user_info") ?: return null
            return UserCard(
                portrait = info.optString("portrait", ""),
                nameShow = info.optString("name_show", ""),
                tiebaUid = info.optString("tieba_uid", "")
                    .ifEmpty { info.optString("user_id", "") }
                    .filter { it.isDigit() }
                    .toLongOrNull() ?: 0L,
                sex = info.intOf("sex"),
                tbAge = info.optString("tb_age", ""),
                agreeNum = info.intOf("total_agree_num"),
                fansNum = info.intOf("fans_num"),
                concernNum = info.intOf("concern_num"),
                intro = info.optString("intro", ""),
                ipAddress = info.optString("ip_address", ""),
                hasConcerned = info.intOf("has_concerned") != 0,
            )
        }
    }
}
