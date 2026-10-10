// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 贴吧品牌蓝，作为 miuix 主题的种子色。 */
val TiebaKeyColor: Color = Color(0xFF3482FF)

/**
 * 应用主题。
 *
 * 使用 miuix 的 [ThemeController] 从种子色推导整套 Material You 色彩方案，
 * 并跟随系统深色模式（[ColorSchemeMode.MonetSystem]）。
 */
@Composable
fun TiebaTheme(
    colorMode: ColorSchemeMode = ColorSchemeMode.MonetSystem,
    keyColor: Color = TiebaKeyColor,
    content: @Composable () -> Unit,
) {
    val controller = remember(colorMode, keyColor) {
        ThemeController(
            colorSchemeMode = colorMode,
            keyColor = keyColor,
        )
    }
    MiuixTheme(controller = controller, content = content)
}