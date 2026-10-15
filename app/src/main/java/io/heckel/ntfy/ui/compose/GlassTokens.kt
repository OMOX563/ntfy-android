package io.heckel.ntfy.ui.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * FxxkPush 液态玻璃 · Compose 设计令牌（P2 重做）
 *
 * 与 res/values/colors.xml 的 View 侧令牌**并存而不互转**：
 *   · View 侧（colors.xml）继续服务 SettingsActivity / Dialog / 通知 RemoteViews，
 *     这些地方换不起，也不该换。
 *   · Compose 侧（本文件）只服务重写的列表与详情页。
 * 两套值必须一致，docs/design-system.md 是唯一事实源。
 *
 * 玻璃参数为什么是这组值（2026-10-15 实测，别随手改）：
 *   初版「氛围斑 8% + 卡片 62% 白」叠出来是 (242,246,252) —— 几乎纯白，色斑被吃光，
 *   看上去就是"官方 ntfy 加了张图"。现在「斑 26/20/16% + 卡片 44% 白」叠出 (215,226,248)，
 *   品牌蓝明确透上来，同时 ink 13.7:1、ink-muted 5.8:1，仍高于 §8 要求的 7:1 / 4.5:1。
 */
@Immutable
data class GlassTokens(
    val brand: Color,
    val brandDim: Color,
    val brandContainer: Color,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassStroke: Color,
    val glassStrokeDim: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val bgBase: Color,
    val bgVeil: Color,
    val alertHigh: Color,
    val alertMid: Color,
    val alertLow: Color,
    val ok: Color,
)

/** 浅色：与 values/colors.xml 逐值对齐 */
val LightGlassTokens = GlassTokens(
    brand = Color(0xFF2C6BED),
    brandDim = Color(0xFF1E4FB0),
    brandContainer = Color(0xFFDCE7FF),
    // 44% 白（原 62%）
    glassFill = Color(0xFF70FFFFFF),
    glassFillStrong = Color(0xFFC7FFFFFF),
    // 80% 白（原 70%）
    glassStroke = Color(0xCCFFFFFF),
    glassStrokeDim = Color(0x140F172A),
    ink = Color(0xFF0F172A),
    inkMuted = Color(0xFF475569),
    inkFaint = Color(0xFF7C8899),
    bgBase = Color(0xFFEEF2F7),
    bgVeil = Color(0x66FFFFFF),
    alertHigh = Color(0xFFB42318),
    alertMid = Color(0xFFC2410C),
    alertLow = Color(0xFF996F00),
    ok = Color(0xFF268257),
)

/** 深色：与 values-night/colors.xml 逐值对齐 */
val DarkGlassTokens = GlassTokens(
    brand = Color(0xFF8FB6FF),
    brandDim = Color(0xFFB8CFF8),
    brandContainer = Color(0xFF1C3A66),
    glassFill = Color(0x701A2230),
    glassFillStrong = Color(0xB81E2836),
    glassStroke = Color(0x33FFFFFF),
    glassStrokeDim = Color(0x3D000000),
    ink = Color(0xFFE8EEF7),
    inkMuted = Color(0xFF94A3B8),
    inkFaint = Color(0xFF64748B),
    bgBase = Color(0xFF0B0F16),
    bgVeil = Color(0x4D000000),
    alertHigh = Color(0xFFFF6B6B),
    alertMid = Color(0xFFFF8B3D),
    alertLow = Color(0xFFFFD426),
    ok = Color(0xFF4CC38A),
)

val LocalGlassTokens = staticCompositionLocalOf { LightGlassTokens }
