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
 * 帖子详情的宿主 Activity。
 *
 * [ThreadDetailPage] 需要 `LocalHistoryStore` 与 `LocalSettingsStore`，
 * 而从搜索页 / 历史页 / 吧详情页点进帖子时已经在 MainActivity 之外了，
 * 所以在独立 Activity 里直接把同一套 CompositionLocal 提供出来，
 * 页面本身与首页点进来的完全一致（同样会记录浏览历史）。
 */
class ThreadDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as TiebaApplication
        val tid = intent.getLongExtra(EXTRA_TID, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val forumName = intent.getStringExtra(EXTRA_FORUM_NAME).orEmpty()

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
                    ThreadDetailPage(
                        repository = app.repository,
                        tid = tid,
                        title = title,
                        forumName = forumName,
                        onBack = { finish() },
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TID = "tid"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_FORUM_NAME = "forum_name"

        fun intent(
            context: Context,
            tid: Long,
            title: String = "",
            forumName: String = "",
        ): Intent = Intent(context, ThreadDetailActivity::class.java)
            .putExtra(EXTRA_TID, tid)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_FORUM_NAME, forumName)
    }
}
