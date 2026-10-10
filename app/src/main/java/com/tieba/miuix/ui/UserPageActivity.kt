// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.tieba.miuix.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.tieba.miuix.TiebaApplication
import com.tieba.miuix.ui.theme.TiebaTheme

/**
 * 用户资料页的宿主 Activity。
 *
 * 从帖子楼层、搜索结果、吧详情等处点用户头像都会走到这里；
 * 页面里点一条发言会再开一个 [ThreadDetailActivity]，形成正常的 Activity 栈。
 */
class UserPageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as TiebaApplication
        val portrait = intent.getStringExtra(EXTRA_PORTRAIT).orEmpty()
        val uid = intent.getLongExtra(EXTRA_UID, 0L)
        val host = this

        setContent {
            TiebaTheme(
                settings = app.settingsStore.settings,
                fontScale = app.settingsStore.fontScale,
            ) {
                CompositionLocalProvider(
                    LocalSettingsStore provides app.settingsStore,
                    LocalHistoryStore provides app.historyStore,
                    LocalSearchHistoryStore provides app.searchHistoryStore,
                    LocalAccountStore provides app.accountStore,
                    LocalRepository provides app.repository,
                ) {
                    UserPage(
                        portrait = portrait,
                        uid = uid,
                        onBack = { finish() },
                        onOpenThread = { tid, title, forumName ->
                            startActivity(ThreadDetailActivity.intent(host, tid, title, forumName))
                        },
                        onOpenFollowList = { targetUid, name ->
                            startActivity(FollowListActivity.intent(host, targetUid, name + " 的关注"))
                        },
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_PORTRAIT = "portrait"
        private const val EXTRA_UID = "uid"

        /**
         * 打开用户资料页。
         *
         * @param portrait 帖子里拿到的 portrait（纯串或完整 URL 都可以）
         * @param uid 可选，带上才能在资料页里列出这个人的发言
         */
        fun intent(context: Context, portrait: String, uid: Long = 0L): Intent =
            Intent(context, UserPageActivity::class.java)
                .putExtra(EXTRA_PORTRAIT, portrait)
                .putExtra(EXTRA_UID, uid)
    }
}
