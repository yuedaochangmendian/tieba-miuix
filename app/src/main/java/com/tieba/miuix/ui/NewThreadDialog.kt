// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 主题标题与正文的长度上限，服务端也会拦，这里先在本地挡一道。 */
private const val THREAD_TITLE_MAX = 40
private const val THREAD_CONTENT_MAX = 5000

/**
 * 发主题帖的编辑框。
 *
 * 只收集标题与正文，网络请求由调用方发起 —— 这样发帖成功后调用方可以顺手刷新
 * 吧内列表。标题留空即「无标题帖」（对应接口的 `is_ntitle=0`）。
 */
@Composable
fun NewThreadDialog(
    forumName: String,
    sending: Boolean,
    errorMessage: String,
    onSend: (title: String, content: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }

    val message = errorMessage.ifBlank { localError }

    Dialog(onDismissRequest = { if (!sending) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(16.dp),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHigh),
        ) {
            Text(
                text = "发帖到「" + forumName + "」",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceContainerHigh,
            )
            TextField(
                value = title,
                onValueChange = {
                    title = it.take(THREAD_TITLE_MAX)
                    localError = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                label = "标题（可不填）",
                useLabelAsPlaceholder = true,
                singleLine = true,
                enabled = !sending,
            )
            TextField(
                value = content,
                onValueChange = {
                    content = it.take(THREAD_CONTENT_MAX)
                    localError = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .heightIn(min = 140.dp),
                label = "正文",
                useLabelAsPlaceholder = true,
                enabled = !sending,
            )
            Text(
                text = content.length.toString() + " / " + THREAD_CONTENT_MAX,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                style = MiuixTheme.textStyles.footnote2,
                textAlign = TextAlign.End,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (message.isNotBlank()) {
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !sending,
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(text = "取消", style = MiuixTheme.textStyles.button)
                }
                Button(
                    onClick = {
                        if (content.isBlank()) {
                            localError = "正文不能为空"
                        } else {
                            onSend(title.trim(), content)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !sending,
                ) {
                    Text(
                        text = if (sending) "发送中…" else "发布",
                        style = MiuixTheme.textStyles.button,
                    )
                }
            }
        }
    }
}
