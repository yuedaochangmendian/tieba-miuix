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
 * 热话题详情的宿主 Activity。
 *
 * 与 [ThreadDetailActivity] 同样的做法：在独立 Activity 里把同一套
 * CompositionLocal 提供出来，这样从首页「热聊」点进来时页面行为完全一致。
 */
class TopicDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as TiebaApplication
        val topicId = intent.getLongExtra(EXTRA_TOPIC_ID, 0L)
        val topicName = intent.getStringExtra(EXTRA_TOPIC_NAME).orEmpty()

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
                    TopicDetailPage(
                        repository = app.repository,
                        topicId = topicId,
                        topicName = topicName,
                        onBack = { finish() },
                        onOpenThread = { tid, title, forumName ->
                            startActivity(
                                ThreadDetailActivity.intent(this, tid, title, forumName),
                            )
                        },
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TOPIC_ID = "topic_id"
        private const val EXTRA_TOPIC_NAME = "topic_name"

        fun intent(context: Context, topicId: Long, topicName: String): Intent =
            Intent(context, TopicDetailActivity::class.java)
                .putExtra(EXTRA_TOPIC_ID, topicId)
                .putExtra(EXTRA_TOPIC_NAME, topicName)
    }
}
