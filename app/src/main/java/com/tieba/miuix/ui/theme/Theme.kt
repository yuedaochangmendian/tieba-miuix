// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.tieba.miuix.data.DarkMode
import com.tieba.miuix.data.Settings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/** 默认强调色。 */
val TiebaKeyColor = Color(0xFF3482FF)

/**
 * 应用主题。
 *
 * 深色模式与字号都来自用户设置：
 * - 深色模式靠**重建** [ThemeController] 生效，因为 `colorSchemeMode` 是只读属性，
 *   不能在外部重新赋值。
 * - 字号靠给 [MiuixTheme] 传一份按比例缩放过的 [TextStyles] 生效。
 *   miuix 的 `TextStyles` 属性都是 `internal set`，唯一公开的构造入口是 `copy(...)`，
 *   所以这里用 `defaultTextStyles().copy(...)` 而不是逐个赋值。
 *
 * 注意 `remember` 的 key 必须带上 `fontScale`：miuix 内部缓存 textStyles 时没有把
 * 参数当 key，只有传入新实例才会重新广播给子树。
 */
@Composable
fun TiebaTheme(
    settings: Settings,
    fontScale: Float,
    keyColor: Color = TiebaKeyColor,
    content: @Composable () -> Unit,
) {
    val mode = when (settings.darkMode) {
        DarkMode.ALWAYS_OFF -> ColorSchemeMode.MonetLight
        DarkMode.ALWAYS_ON -> ColorSchemeMode.MonetDark
        else -> ColorSchemeMode.MonetSystem
    }
    val controller = remember(mode, keyColor) {
        ThemeController(colorSchemeMode = mode, keyColor = keyColor)
    }
    val textStyles = remember(fontScale) {
        scaledTextStyles(fontScale)
    }
    MiuixTheme(controller = controller, textStyles = textStyles, content = content)
}

/**
 * 按比例缩放全部字号。
 *
 * 行高在 miuix 里是用 `em` 表示的相对值，会跟着字号一起变化，
 * 所以这里只要缩放 `fontSize` 就够，不必逐个调整 `lineHeight`。
 */
private fun scaledTextStyles(scale: Float): TextStyles {
    val base = defaultTextStyles()
    return base.copy(
        main = base.main.scaled(scale),
        paragraph = base.paragraph.scaled(scale),
        body1 = base.body1.scaled(scale),
        body2 = base.body2.scaled(scale),
        button = base.button.scaled(scale),
        footnote1 = base.footnote1.scaled(scale),
        footnote2 = base.footnote2.scaled(scale),
        headline1 = base.headline1.scaled(scale),
        headline2 = base.headline2.scaled(scale),
        subtitle = base.subtitle.scaled(scale),
        title1 = base.title1.scaled(scale),
        title2 = base.title2.scaled(scale),
        title3 = base.title3.scaled(scale),
        title4 = base.title4.scaled(scale),
    )
}

private fun TextStyle.scaled(scale: Float): TextStyle = copy(fontSize = fontSize.scaled(scale))

private fun TextUnit.scaled(scale: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else (value * scale).sp
