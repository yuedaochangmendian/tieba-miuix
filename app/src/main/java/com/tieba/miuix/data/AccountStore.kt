// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.data

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 当前登录账号。
 *
 * 只保存真正需要的凭证：
 *  - [bduss] / [stoken]：登录会话，所有请求都要带上；
 *  - [tbs]：写操作（发帖、回复、签到）所需的 CSRF 令牌，**必须在登录后
 *    另行取一次**，只存 BDUSS 会导致「读能跑、写全挂」。
 *
 * [uid] 与 [userName] 仅用于界面展示，取不到时留空即可。
 */
@Stable
class AccountStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** `BDUSS`。空串表示未登录。 */
    var bduss by mutableStateOf(prefs.getString(KEY_BDUSS, "").orEmpty())
        private set

    /** `STOKEN`。 */
    var stoken by mutableStateOf(prefs.getString(KEY_STOKEN, "").orEmpty())
        private set

    /** `tbs`，写操作令牌。 */
    var tbs by mutableStateOf(prefs.getString(KEY_TBS, "").orEmpty())
        private set

    /** 用户 id（`uid`），仅展示用。 */
    var uid by mutableStateOf(prefs.getString(KEY_UID, "").orEmpty())
        private set

    /** 用户名，仅展示用。 */
    var userName by mutableStateOf(prefs.getString(KEY_USER_NAME, "").orEmpty())
        private set

    /** 昵称，仅展示用。 */
    var nameShow by mutableStateOf(prefs.getString(KEY_NAME_SHOW, "").orEmpty())
        private set

    /** 是否已登录。 */
    val isLoggedIn: Boolean get() = bduss.isNotEmpty()

    /** 界面展示名：昵称优先，其次用户名。 */
    val displayName: String get() = nameShow.ifEmpty { userName }

    /** tbs 是否已就绪（写操作需要）。 */
    val hasTbs: Boolean get() = tbs.isNotEmpty()

    /** 生成仓库用的凭证快照。 */
    fun credentials(): Credentials = Credentials(bduss = bduss, stoken = stoken, tbs = tbs)

    /**
     * 登录成功后写入账号。
     *
     * 传空值不会覆盖已有值，避免「补充信息」时把凭证抹掉。
     */
    fun save(
        bduss: String = "",
        stoken: String = "",
        tbs: String = "",
        uid: String = "",
        userName: String = "",
        nameShow: String = "",
    ) {
        val editor = prefs.edit()
        if (bduss.isNotEmpty()) {
            this.bduss = bduss
            editor.putString(KEY_BDUSS, bduss)
        }
        if (stoken.isNotEmpty()) {
            this.stoken = stoken
            editor.putString(KEY_STOKEN, stoken)
        }
        if (tbs.isNotEmpty()) {
            this.tbs = tbs
            editor.putString(KEY_TBS, tbs)
        }
        if (uid.isNotEmpty()) {
            this.uid = uid
            editor.putString(KEY_UID, uid)
        }
        if (userName.isNotEmpty()) {
            this.userName = userName
            editor.putString(KEY_USER_NAME, userName)
        }
        if (nameShow.isNotEmpty()) {
            this.nameShow = nameShow
            editor.putString(KEY_NAME_SHOW, nameShow)
        }
        editor.apply()
    }

    /** 单独更新 tbs（登录后补取时用）。 */
    fun saveTbs(value: String) {
        if (value.isEmpty()) return
        tbs = value
        prefs.edit().putString(KEY_TBS, value).apply()
    }

    /** 退出登录，清空全部凭证。 */
    fun clear() {
        bduss = ""
        stoken = ""
        tbs = ""
        uid = ""
        userName = ""
        nameShow = ""
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "tieba_miuix_account"
        private const val KEY_BDUSS = "bduss"
        private const val KEY_STOKEN = "stoken"
        private const val KEY_TBS = "tbs"
        private const val KEY_UID = "uid"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_NAME_SHOW = "name_show"

        /**
         * 从 Cookie 字符串里取某个键。
         *
         * WebView 的 `CookieManager.getCookie()` 返回的是 `k=v; k2=v2` 形式，
         * 值可能仍带 URL 编码，这里做一次解码；键名大小写不敏感。
         */
        fun cookieValue(cookies: String?, key: String): String {
            if (cookies.isNullOrEmpty()) return ""
            return cookies.split(';')
                .asSequence()
                .map { it.trim() }
                .firstOrNull { it.substringBefore('=').equals(key, ignoreCase = true) }
                ?.substringAfter('=', "")
                ?.let { decodeCookieValue(it) }
                .orEmpty()
        }

        private fun decodeCookieValue(raw: String): String {
            if (!raw.contains('%')) return raw
            return runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        }
    }
}
