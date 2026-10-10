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
 * 关注列表的宿主 Activity。
 *
 * 从用户资料页点「关注」进来；点列表里的人会再开一个 [UserPageActivity]。
 */
class FollowListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as TiebaApplication
        val uid = intent.getLongExtra(EXTRA_UID, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
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
                    FollowListPage(
                        repository = app.repository,
                        uid = uid,
                        title = title,
                        onBack = { finish() },
                        onOpenUser = { targetUid, portrait, _ ->
                            startActivity(UserPageActivity.intent(host, portrait, targetUid))
                        },
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_UID = "uid"
        private const val EXTRA_TITLE = "title"

        /**
         * 打开关注列表。
         *
         * @param uid 0 表示当前登录用户的关注
         */
        fun intent(context: Context, uid: Long, title: String = ""): Intent =
            Intent(context, FollowListActivity::class.java)
                .putExtra(EXTRA_UID, uid)
                .putExtra(EXTRA_TITLE, title)
    }
}
