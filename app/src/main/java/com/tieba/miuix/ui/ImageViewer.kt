// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import com.tieba.miuix.data.saveImageToGallery
import com.tieba.miuix.protocol.PbContent
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme
import android.widget.Toast

/**
 * 一张待查看的图片。
 *
 * 宽高来自楼层协议里的 `width` / `height`，为 0 时按 4:3 估算，
 * 保证列表还没加载出图片时高度也不会来回跳动。
 */
data class ImageItem(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 4f / 3f
}

/**
 * 楼层 / 楼中楼配图的缩略图列表。
 *
 * 单图按原比例展示（并限制最大高度），多图排成两列网格。
 * 点任意一张都会打开大图查看器，并定位到被点的那张。
 */
@Composable
fun PostImageGrid(
    images: List<ImageItem>,
    onOpenImage: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) return

    if (images.size == 1) {
        val ratio = images[0].aspectRatio.coerceIn(0.5f, 3f)
        SafeImage(
            url = images[0].url,
            modifier = modifier
                .fillMaxWidth(0.68f)
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpenImage(0) },
            contentDescription = "配图",
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        images.chunked(2).forEachIndexed { rowIndex, rowImages ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowImages.forEachIndexed { columnIndex, image ->
                    val index = rowIndex * 2 + columnIndex
                    SafeImage(
                        url = image.url,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenImage(index) },
                        contentDescription = "配图 " + (index + 1),
                    )
                }
                // 奇数张时补一个空位，避免最后一张被拉伸成整行宽。
                if (rowImages.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 从楼层内容中挑出图片，保持原有顺序并去重。 */
fun List<PbContent>.toImages(): List<ImageItem> {
    val result = ArrayList<ImageItem>()
    val seen = HashSet<String>()
    for (item in this) {
        if (item.type != 3 && item.type != 20) continue
        val url = item.bestImageUrl
        if (url.isBlank() || !seen.add(url)) continue
        result.add(ImageItem(url = url, width = item.width, height = item.height))
    }
    return result
}

/**
 * 全屏大图查看器。
 *
 * 左右滑动切换、捏合放大、拖动平移、双击复位；点关闭按钮或系统返回键退出。
 * 与设置保持一致：无图模式下不请求图片，只显示占位提示。
 */
@Composable
fun ImageViewerDialog(
    images: List<ImageItem>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (images.isEmpty()) return
    val start = initialIndex.coerceIn(0, images.lastIndex)
    // 大图查看属于用户主动行为，不套用列表的省流量策略。
    val settings = currentSettings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savingUrl by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pagerState = rememberPagerState(initialPage = start, pageCount = { images.size })

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.97f)),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (pagerState.currentPage + 1).toString() + " / " + images.size,
                        modifier = Modifier.weight(1f),
                        style = MiuixTheme.textStyles.body1,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val currentUrl = images.getOrNull(pagerState.currentPage)?.url.orEmpty()
                    IconButton(
                        onClick = {
                            if (savingUrl.isNotEmpty() || currentUrl.isEmpty()) return@IconButton
                            savingUrl = currentUrl
                            scope.launch {
                                val fileName = "tieba_" + System.currentTimeMillis() + ".jpg"
                                val result = runCatching {
                                    saveImageToGallery(context, currentUrl, fileName)
                                }
                                savingUrl = ""
                                Toast.makeText(
                                    context,
                                    result.fold(
                                        onSuccess = { "已保存到相册：" + it },
                                        onFailure = { it.message ?: "保存失败" },
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        enabled = savingUrl.isEmpty(),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Download,
                            contentDescription = "保存到相册",
                            tint = if (savingUrl.isEmpty()) Color.White else Color.White.copy(alpha = 0.4f),
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = "关闭",
                            tint = Color.White,
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { page ->
                    ZoomableImage(
                        image = images[page],
                        onTap = onDismiss,
                        zoomEnabled = settings.shouldLoadImage(true),
                    )
                }
            }
        }
    }
}

/** 单张大图：支持双击/捏合缩放与拖动。 */
@Composable
private fun ZoomableImage(
    image: ImageItem,
    onTap: () -> Unit,
    zoomEnabled: Boolean,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()

        fun clamp() {
            val halfWidth = viewportWidth * scale / 2f
            val halfHeight = viewportHeight * scale / 2f
            val maxX = (halfWidth - viewportWidth / 2f).coerceAtLeast(0f)
            val maxY = (halfHeight - viewportHeight / 2f).coerceAtLeast(0f)
            offsetX = offsetX.coerceIn(-maxX, maxX)
            offsetY = offsetY.coerceIn(-maxY, maxY)
        }

        val transformModifier = if (!zoomEnabled) {
            Modifier
        } else {
            Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = {
                            if (scale > 1.02f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                                clamp()
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                        clamp()
                    }
                }
        }

        SubcomposeAsyncImage(
            model = image.url,
            contentDescription = "大图",
            modifier = Modifier
                .fillMaxSize()
                .then(transformModifier)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            contentScale = ContentScale.Fit,
            loading = { ViewerPlaceholder(text = "加载中…") },
            error = { ViewerPlaceholder(text = "图片加载失败") },
        )
    }
}

@Composable
private fun ViewerPlaceholder(text: String) {
    Box(
        modifier = Modifier
            .size(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MiuixTheme.textStyles.footnote1,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
    }
}

/** 页码选择弹窗，用于「跳页」。 */
@Composable
fun PageJumpDialog(
    currentPage: Int,
    totalPage: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (totalPage <= 0) return
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(16.dp),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHigh),
        ) {
            Text(
                text = "跳转到第几页（共 " + totalPage + " 页）",
                style = MiuixTheme.textStyles.subtitle,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val pageCount = totalPage.coerceAtMost(300)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(count = pageCount) { index ->
                        val page = index + 1
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 10.dp,
                            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            colors = CardDefaults.defaultColors(
                                color = if (page == currentPage) {
                                    MiuixTheme.colorScheme.primaryContainer
                                } else {
                                    MiuixTheme.colorScheme.surfaceContainer
                                },
                                contentColor = if (page == currentPage) {
                                    MiuixTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceContainer
                                },
                            ),
                            onClick = {
                                onSelect(page)
                                onDismiss()
                            },
                        ) {
                            Text(
                                text = "第 " + page + " 页",
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一个圆形小标签，用于「N / 总数」这类角标。 */
@Composable
fun RoundBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            color = Color.White,
        )
    }
}
