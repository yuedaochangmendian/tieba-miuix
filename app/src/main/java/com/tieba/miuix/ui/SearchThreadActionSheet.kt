package com.tieba.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tieba.miuix.protocol.ThreadSearchItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 搜索结果卡片的长按菜单。
 *
 * 搜索接口给的是 [ThreadSearchItem]，和吧内列表用的 `ThreadInfo` 不是一个模型，
 * 所以这里单独一份；动作只有「复制链接 / 分享 / 看作者」这几样能对上。
 */
@Composable
fun SearchThreadActionSheet(
    item: ThreadSearchItem,
    onDismiss: () -> Unit,
    onNote: (String) -> Unit,
) {
    val context = LocalContext.current
    BottomActionSheet(
        title = item.title.ifEmpty { "（无标题）" },
        onDismiss = onDismiss,
    ) {
        ActionSheetRow(
            text = "复制链接",
            onClick = {
                onNote(if (copyThreadLink(context, item.tid)) "链接已复制" else "复制失败")
            },
            onAfter = onDismiss,
        )
        ActionSheetRow(
            text = "分享",
            onClick = {
                if (!shareThread(context, item.tid, item.title)) {
                    onNote("没有找到可以分享的应用")
                }
            },
            onAfter = onDismiss,
        )
    }
}

/** 动作表的统一外壳：底部弹出一张卡片，第一行是标题；下方还有一张「取消」卡片。 */
@Composable
fun BottomActionSheet(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column {
                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                        )
                        HorizontalDivider()
                    }
                    actions()
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                cornerRadius = 18.dp,
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHigh),
            ) {
                ActionSheetRow(text = "取消", onClick = onDismiss)
            }
        }
    }
}

/** 动作表里的一行。 */
@Composable
fun ActionSheetRow(
    text: String,
    onClick: () -> Unit,
    onAfter: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 0.dp,
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHigh),
        pressFeedbackType = top.yukonga.miuix.kmp.utils.PressFeedbackType.Sink,
        showIndication = true,
        onClick = {
            onClick()
            onAfter()
        },
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceContainerHigh,
        )
    }
}
