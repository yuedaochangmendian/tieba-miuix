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

/** 回帖内容长度上限，服务端也会拦，这里先在本地挡一道。 */
private const val REPLY_MAX_LENGTH = 1000

/**
 * 回帖编辑框。
 *
 * 只负责收集内容并把结果交回给调用方，网络请求依旧在页面里发起 ——
 * 这样回复成功后页面可以顺手刷新列表。
 *
 * @param forumName 目标吧名，显示在标题里
 * @param sending 正在发送中，此时按钮变灰、不可重复点
 * @param errorMessage 上一次发送失败的原因，非空时显示在编辑框下方
 * @param onSend 点「发送」时回调，参数是正文
 */
@Composable
fun ReplyDialog(
    title: String,
    forumName: String,
    sending: Boolean,
    errorMessage: String,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
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
                text = "回复" + if (forumName.isBlank()) "" else "「" + forumName + "」",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceContainerHigh,
            )
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                )
            }
            TextField(
                value = content,
                onValueChange = {
                    content = it.take(REPLY_MAX_LENGTH)
                    localError = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .heightIn(min = 120.dp),
                label = "说点什么…",
                useLabelAsPlaceholder = true,
                enabled = !sending,
            )
            Text(
                text = content.length.toString() + " / " + REPLY_MAX_LENGTH,
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
                            localError = "回复内容不能为空"
                        } else {
                            onSend(content)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !sending,
                ) {
                    Text(
                        text = if (sending) "发送中…" else "发送",
                        style = MiuixTheme.textStyles.button,
                    )
                }
            }
        }
    }
}
