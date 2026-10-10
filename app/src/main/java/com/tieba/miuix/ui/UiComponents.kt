// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.tieba.miuix.TiebaApplication
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 图片占位块。
 *
 * 不加载图片（无图模式 / 非 Wi-Fi 且省流量）或加载失败时，都渲染成一块
 * 带「图」字提示的占位区域，保证列表高度稳定，不会因为图片忽有忽无而跳动。
 */
@Composable
fun SafeImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    cornerRadius: Dp = 10.dp,
) {
    val context = LocalContext.current
    val settings = currentSettings
    val onWifi = remember(context) { TiebaApplication.isOnWifi(context) }
    val shape = RoundedCornerShape(cornerRadius)

    if (url.isBlank() || !settings.shouldLoadImage(onWifi)) {
        ImagePlaceholder(modifier = modifier, shape = shape)
        return
    }

    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier.clip(shape),
        contentScale = ContentScale.Crop,
        loading = { ImagePlaceholder(modifier = Modifier.fillMaxSize(), shape = shape) },
        error = { ImagePlaceholder(modifier = Modifier.fillMaxSize(), shape = shape) },
    )
}

@Composable
private fun ImagePlaceholder(modifier: Modifier, shape: RoundedCornerShape) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "图",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 错误 / 空状态提示卡。 */
@Composable
fun MessageCard(
    text: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 「加载更多 / 没有更多了」行。 */
@Composable
fun LoadMoreRow(
    hasMore: Boolean,
    isLoading: Boolean,
    visible: Boolean,
    onLoadMore: () -> Unit,
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (hasMore) {
            Button(onClick = onLoadMore, enabled = !isLoading) {
                Text(
                    text = if (isLoading) "加载中…" else "加载更多",
                    style = MiuixTheme.textStyles.button,
                )
            }
        } else {
            Text(
                text = "没有更多了",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 分组小标题。 */
@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
    )
}

/** 一组设置项，外观是一张卡里若干可点行。 */
@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(vertical = 4.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        content()
    }
}

/** 开关型设置项。 */
@Composable
fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary.ifEmpty { null },
        endActions = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

/**
 * 单选型设置项：折叠时只显示「当前值」，点开后在下面展开全部选项。
 *
 * 用展开代替弹窗，少一层浮层状态，也更符合 miuix 设置页的常见做法。
 */
@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.second == selected }?.first.orEmpty()

    BasicComponent(
        title = title,
        summary = if (expanded) null else currentLabel,
        onClick = { expanded = !expanded },
        bottomAction = if (!expanded) {
            null
        } else {
            {
                Column(modifier = Modifier.fillMaxWidth()) {
                    options.forEach { (label, value) ->
                        ChoiceItem(
                            label = label,
                            selected = value == selected,
                            onClick = {
                                onSelected(value)
                                expanded = false
                            },
                        )
                    }
                }
            }
        },
    )
}

/**
 * 展开后的一个单选项。
 *
 * 用 [Card] 的可点重载承载点击：miuix 没有公开 `Modifier.clickable` 之类的工具，
 * 而 `Card` 自带水波纹与按压反馈，正好可以当「可点整行」用。
 */
@Composable
private fun ChoiceItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        cornerRadius = 8.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = CardDefaults.defaultColors(
            color = if (selected) {
                MiuixTheme.colorScheme.primaryContainer
            } else {
                MiuixTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (selected) {
                MiuixTheme.colorScheme.onPrimaryContainer
            } else {
                MiuixTheme.colorScheme.onSurfaceContainer
            },
        ),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.body2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = "已选择",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 居中显示的页面级提示（用于空页面）。 */
@Composable
fun CenterHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 32.dp),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 可点击的整行（用于「清空」「跳转」这类动作）。 */
@Composable
fun ActionRow(
    title: String,
    summary: String = "",
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary.ifEmpty { null },
        onClick = onClick,
    )
}
