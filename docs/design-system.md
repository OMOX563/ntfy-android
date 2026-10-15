# FxxkPush Android 液态玻璃设计系统

> 目标：把 ntfy-android 原生的 Material3 卡片流，换成「液态玻璃」质感的通知客户端。
> 审美基准（用户既有档案）：**透明玻璃卡片 · 克制的模糊 · Q 弹按压 · 精简做减法**。

---

## 1. 设计原则

| 原则 | 具体化 |
|---|---|
| **玻璃是底，不是装饰** | 卡片半透明 + 细边框高光，不叠无意义的渐变与阴影堆叠 |
| **模糊必须克制** | Android 上没有真 backdrop-filter，用 `alpha + 边框高光 + 轻阴影` 近似；不做重度模糊糊字 |
| **信息层级优先** | 玻璃效果不得牺牲对比度：正文 WCAG AA（4.5:1）起，大字 3:1 起 |
| **做减法** | 删冗余装饰；空状态用一行字 + 一个符号，不堆插画与说明段落 |
| **动效有目的** | 只为「状态变化」服务：按压反馈、入场、内容更新；不做循环装饰动画 |

---

## 2. 色彩体系（浅/深双套）

### 2.1 品牌基色

| 名称 | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `brand` | `#2C6BED` | `#8FB6FF` | 主操作、选中态、进度 |
| `brand-dim` | `#1E4FB0` | `#B8CFF8` | 品牌文字（浅底） |
| `brand-container` | `#DCE7FF` | `#1C3A66` | 品牌背景块 |

### 2.2 玻璃表面（核心）

Android 无真 backdrop blur，用**半透明填充 + 内描边高光 + 轻投影**近似：

| 名称 | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `glass-fill` | `#FFFFFF` @ **62%** | `#1A2230` @ **58%** | 卡片主面 |
| `glass-fill-strong` | `#FFFFFF` @ **78%** | `#1E2836` @ **72%** | 需要更高可读性的块（弹窗、输入区） |
| `glass-stroke` | `#FFFFFF` @ **70%** | `#FFFFFF` @ **12%** | 1px 内描边（亮色下模拟玻璃边缘高光） |
| `glass-stroke-dim` | `#0F172A` @ **8%** | `#000000` @ **24%** | 暗侧描边，制造厚度 |

### 2.3 前景（玻璃上必须过 AA）

| 名称 | 浅色 | 深色 | 对比度目标 |
|---|---|---|---|
| `ink` | `#0F172A` | `#E8EEF7` | ≥ 12:1（正文） |
| `ink-muted` | `#475569` | `#94A3B8` | ≥ 4.5:1（次要文字） |
| `ink-faint` | `#7C8899` | `#64748B` | ≥ 3:1（图标、时间戳） |

### 2.4 语义色（沿用 ntfy 优先级语义）

| 名称 | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `alert-high` | `#E5484D` | `#FF6369` | priority 4-5（紧急） |
| `alert-mid` | `#C2410C` | `#FF8B3D` | priority 3 |
| `alert-low` | `#996F00` | `#FFD426` | priority 1-2 |
| `ok` | `#268257` | `#4CC38A` | 已读/成功 |

> **实测校准（WCAG 合成计算，玻璃底 = 白 62% 叠 #EEF2F7 → 实色 #F9FAFC）**：
> 首版选的 `#F76B15`(2.84:1) 与 `#F5C518`(1.56:1)、`#30A46C`(3.02:1) 在浅色玻璃上
> **不达标**（图标需 ≥3:1），已按实测换成上表值：
> 浅色 alert-mid **4.96:1** · alert-low **4.34:1** · ok **4.56:1**；
> 深色 alert-mid 7.48:1 · alert-low 12.20:1 · alert-high 6.01:1。
> 正文与次要文字浅色 17.09:1 / 7.26:1，深色 14.95:1 / 6.80:1，均远超 AA。
> **改动语义色后必须重算**——玻璃底会随主题与 alpha 变化，贴线的值随时可能掉下去。

### 2.5 背景（玻璃要透出底色）

| 名称 | 浅色 | 深色 |
|---|---|---|
| `bg-base` | `#EEF2F7` | `#0B0F16` |
| `bg-veil` | `#FFFFFF` @ 40% 渐层 | `#000000` @ 30% 渐层 |

**玻璃层次**：页面底 `bg-base` → 彩色氛围斑（2-3 个低透明度 radial）→ 玻璃卡片浮于其上。氛围斑是玻璃感的来源，但透明度 ≤ 8%，绝不抢内容。

---

## 3. 圆角与间距

| 令牌 | 值 | 用途 |
|---|---|---|
| `radius-card` | 20dp | 主卡片 |
| `radius-inner` | 14dp | 卡片内块（附件框、按钮组） |
| `radius-chip` | 999dp | 徽章 / 标签 |
| `radius-fab` | 18dp | FAB（Material3 默认 16dp，+2 更"软"） |
| `gap-xs` | 4dp | 图标与文字 |
| `gap-s` | 8dp | 同组元素 |
| `gap-m` | 14dp | 卡片内分组 |
| `gap-l` | 22dp | 卡片之间 |
| `page-pad` | 16dp | 页面左右留白 |

---

## 4. 字体与层次（不引入新字体，用系统）

| 层级 | size / weight | 用途 |
|---|---|---|
| Display | 28sp / 700 | 空状态主文案 |
| Title | 20sp / 650 | 页面标题 |
| Card Title | 16sp / 650 | 消息标题 |
| Body | 15sp / 400 | 正文（行高 1.45） |
| Meta | 13sp / 400 | 来源、时间 |
| Micro | 11sp / 500 | 徽章文字（字距 +0.02em） |

---

## 5. 毛玻璃实现方案（Android 12+）

Android 12 (API 31) 起支持 `RenderEffect` 实时模糊，但**对列表滚动项做实时模糊开销大且易掉帧**。采用两级策略：

### 5.1 基础层（全部 Android 8+ 可用）
```xml
<!-- glass_card.xml -->
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/glass_fill"/>          <!-- 62% 白 -->
    <stroke android:width="1dp" android:color="@color/glass_stroke"/>
    <corners android:radius="@dimen/radius_card"/>
</shape>
```
配合 `MaterialCardView`：`cardElevation=0dp`（阴影改由一层极淡投影承担）、`cardBackgroundColor=@android:color/transparent`。

### 5.2 增强层（API 31+ 才启用）
在 `themes.xml` 的 `values-v31` 变体里给卡片容器加：
```xml
<item name="android:background">@drawable/glass_card_blur</item>
```
`glass_card_blur` 不可行（drawable 无法持有 RenderEffect），改为**在 Kotlin 侧对顶层容器设 `setRenderEffect`**，仅对「详情页顶部条」和「空状态面板」这类**静态、非滚动列表**的元素启用。滚动列表里的卡片一律走基础层。

> 决策依据：滚动中每帧跑 RenderEffect 在中低端机上必然掉帧，而 FxxkPush 的主场景是**长列表滚动**。宁可少一点"真玻璃"，也不能卡。

---

## 6. 动效

| 动效 | 参数 | 说明 |
|---|---|---|
| **按压反馈** | scale 1.0 → **0.96**，160ms，`FastOutSlowIn` | 卡片与按钮统一；回弹到 1.0 用 220ms `Overshoot` 微弹（≤ 4% 过冲，克制） |
| **列表入场** | 透明度 0→1 + Y 位移 12dp→0，**220ms**，逐项错峰 24ms | 仅首次加载 / 筛选切换，不在每次滚动触发 |
| **状态更新** | 高度渐变 240ms（`LinearOutSlowIn`） | 新消息插入不跳变 |
| **空状态** | 符号 0.9→1.0 缩放 260ms | 单次，不循环 |
| 减少动画 | 全部尊重 `Settings.Global.ANIMATOR_DURATION_SCALE = 0` | 无障碍 |

---

## 7. 改造范围与顺序

| 阶段 | 文件 | 改动 |
|---|---|---|
| **P0 令牌层** | `values/colors.xml`、`values-night/colors.xml`、`values/dimens.xml`、`values/themes.xml` | 新增玻璃/品牌/前景/语义色 + 圆角与间距；`AppTheme` 引用新令牌 |
| **P1 玻璃形状** | 新增 `drawable/glass_card.xml`、`glass_chip.xml`、`glass_inner.xml` | 三种玻璃材质 |
| **P2 消息卡片** | `fragment_detail_item.xml` + `DetailAdapter.kt` | CardView 换玻璃背景、圆角 20dp、标题 650 字重、正文行高 1.45、来源与时间分层、tags 变玻璃徽章 |
| **P3 订阅行** | `fragment_main_item.xml` + `MainAdapter.kt` | 左侧图标容器变玻璃圆底、状态点用语义色 |
| **P4 页面底** | `activity_detail.xml`、`activity_main.xml` | `bg-base` + 低透明度氛围斑（新增 drawable）；顶部 app bar 半透明 |
| **P5 空状态** | `detail_no_notifications_*`、`main_no_subscriptions_*` | 图标 → 玻璃圆底容器 + 符号；文案精简为一行 |
| **P6 动效** | `MainAdapter.kt`、`DetailAdapter.kt`、`itemAnimator` | 按压、入场、状态更新三组；减少动画兼容 |

---

## 8. 验收标准

- [ ] 浅色/深色两套都成体系，无「深色下白底」「浅色下黑字糊」问题
- [ ] 玻璃卡片在两套主题下，正文对比度 ≥ 4.5:1、次要文字 ≥ 4.5:1、图标 ≥ 3:1
- [ ] 列表滚动 **60fps**（在中端机实测，不接受掉帧换玻璃感）
- [ ] 按压反馈全站一致（卡片、按钮、图标按钮）
- [ ] 系统关闭动画后，无任何残留动效
- [ ] 无障碍：所有图标按钮有 `contentDescription`，触控目标 ≥ 48dp
- [ ] 与 PC 端 GUI 的品牌色一致（同一个 `#2C6BED`）

---

## 9. 明确不做

- ❌ 引入第三方 UI 库（Material 动态色、Glide 之类）——保持 APK 体积与可维护性
- ❌ 滚动列表内的实时 RenderEffect 模糊——性能代价过高
- ❌ 大量自定义插画/装饰图形——与「做减法」冲突
- ❌ 改 Kotlin 业务逻辑——本轮**只动视觉**，逻辑零改动
