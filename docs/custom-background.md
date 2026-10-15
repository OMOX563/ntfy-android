# FxxkPush 液态玻璃 · 自定义背景（v1.1）

> 解决「玻璃没有底色可透，质感出不来」的问题。让用户能放自己的图当底，
> 玻璃卡片浮于其上——和 omox.cc 的思路一致：**模糊克制（4px 档）、主体清晰**。

---

## 1. 背景从哪来（四个档，缺省在中间）

| 档位 | key | 说明 |
|---|---|---|
| **无背景**（默认） | `none` | 只用 `bg_base` 纯色 + 氛围斑。最省、最稳，浅色主题下就是这个 |
| **内置氛围** | `atmosphere` | 现有的 `bg_atmosphere`（三色 radial 斑 + 底色），零配置就有质感 |
| **自定义图片** | `custom` | 用户从相册选一张 |
| **纯色** | `solid` | 用户自选纯色，滑块调色相 |

默认给 **`atmosphere`**（不是 `none`）——用户第一次打开就该看到玻璃效果，
`none` 留给嫌花的人。

## 2. 自定义图片的处理规则

| 规则 | 值 | 理由 |
|---|---|---|
| 存储 | **持久化 URI 权限**（`ACTION_OPEN_DOCUMENT` + `takePersistableUriPermission`） | 用 GetContent 拿不到持久权限，重启后读不到 |
| 内存位图 | 下采样到 **max 1440px** 长边，`RGB_565`（若该图无透明需求） | 4K 原图直读会 OOM；一张 1440px 足够铺满手机 |
| 磁盘缓存 | 缓存目录存一份解码后的 JPEG（质量 85） | 每次进详情页都解原图会卡 |
| 模糊 | **4dp**（`RenderScript` 已弃用 → 用 `RenderEffect`，仅 API 31+；以下降级为不模糊） | 与 omox.cc 同档：隐约可辨形体，不糊到看不清 |
| 缩放 | `centerCrop` + `scale(1.02)` 遮边 | 模糊会在边缘吸出透明边，scale 补掉 |
| 暗色变体 | 自动加一层 `bg_veil`（黑 30%）压暗 | 深色主题下用户选的亮图会晃眼 |
| **自适应遮罩** | 卡片玻璃面**下方**加一层自动明暗的遮罩，见下 | 见 §2.1 |

### 2.1 为什么必须加自适应遮罩（实测）

玻璃是半透明的，用户换什么背景图，卡片上的文字对比度就跟着变。
按 docs/design-system.md §8 的 AA 要求（正文 ≥4.5:1、图标 ≥3:1）实测叠加结果：

| 主题 | 背景图 | 卡片实色 | ink | muted | 结论 |
|---|---|---|---|---|---|
| 浅色 | 纯白 | — | 16.21:1 | 6.88:1 | ✓ |
| 浅色 | **纯黑** | — | 5.87:1 | **2.49:1** | ✗ |
| 浅色 | 深灰 | — | 10.26:1 | **4.35:1** | ✗ 贴线 |
| 深色 | **纯白** | — | **2.88:1** | 1.31:1 | ✗ |
| 深色 | 品牌蓝 | — | 7.18:1 | **3.27:1** | ✗ |

**一张深色或亮色的背景图就能让次要文字掉到 2.5:1 以下。**
这不是配色问题，是半透明材质的固有代价——必须由遮罩兜底。

**做法**（`GlassBackgroundView` 内部）：
1. 解码用户图片时算一次平均亮度与平均色（采样 ≤64 个像素即可，开销可忽略）
2. 在**玻璃卡片所在层之下**（即背景层之上）铺一层全屏遮罩：
   - 背景平均亮度低 → 加黑遮罩，把整体压暗
   - 背景平均亮度高 → 加白遮罩，把整体提亮
3. 遮罩强度按目标反解：**取能让卡片实色与 ink 达到 ≥7:1 的强度**（留余量，不是刚好 4.5）
4. 内置氛围档与纯色档不需要遮罩（我们自己控制底色，对比度已知达标）
5. 遮罩色跟随主题：浅色主题用黑遮罩压暗、深色主题用黑遮罩压暗更狠
   （若反解结果显示需要提亮，才用白遮罩）

**降级**：若因内存/权限拿不到图片、无法采样，直接回退到氛围档（对比度已知达标），
不硬撑。

## 3. 性能红线

- **滚动列表内绝不做实时模糊**——背景图是**固定在页面底层**的，不随列表滚动，
  因此模糊只需算一次（缓存进 Bitmap），不存在每帧重算
- 模糊结果缓存到内存 `LruCache`（上限 1/8 可用内存），切页不重算
- 低内存设备（`ActivityManager.isLowRamDevice`）直接跳过模糊

## 4. 设置项（`main_preferences.xml` 新增一个 PreferenceCategory）

```
外观
├── 背景            [ListPreference] 无背景 / 氛围 / 自定义图片 / 纯色
├── 选择图片        [Preference]     仅当 背景=自定义图片 时显示
├── 背景色          [Preference→ColorPickerDialog]  仅当 背景=纯色 时显示
├── 模糊            [SeekBarPreference] 0-12dp，默认 4
└── 恢复默认        [Preference]
```

**为什么要条件显示**：Preference 没有原生 visible 绑定，用
`onPreferenceChange` 里手动 `isVisible` 控制（项目里 `BasePreferenceFragment.kt`
已有类似做法，照它来）。

## 5. 接入点（三个页面共用）

`activity_main.xml` / `activity_detail.xml` / `activity_settings.xml` 的根容器
（都是 `CoordinatorLayout`，id 是 `app_bar_drawer` 那个 include）——
在 **include 之前**插一层背景 `View`：

```xml
<io.heckel.ntfy.ui.GlassBackgroundView
    android:id="@+id/glass_background"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:contentDescription="@null"
    android:importantForAccessibility="no" />
```

`GlassBackgroundView`（新建，放在 `ui/` 包下）负责：读偏好 → 取对应位图
（纯色/氛围/自定义）→ 可选模糊 → 绘制。`MainActivity` / `DetailActivity` /
`SettingsActivity` 在 `onResume` 调一次 `refresh()`。

## 6. 隐私红线

- **不上传、不联网**——图片只在本机解码与缓存
- 不写 EXIF（`BitmapFactory` 解码本身不带 EXIF，若要存缓存 JPEG 需显式不写 EXIF）
- 缓存文件放 `context.cacheDir`（系统可自动清理）

## 7. 验收

- [ ] 四个档位都能切，切换后立即生效，无需重启
- [ ] 自定义图片：重启 App 后仍在（持久 URI 权限生效）
- [ ] 4K 大图不 OOM（1440px 下采样）
- [ ] 深色主题下亮图被压暗，不晃眼
- [ ] 模糊档位可调，0 = 完全不模糊
- [ ] 条件显示正确：非 custom 时不显示「选择图片」，非 solid 时不显示「背景色」
- [ ] 低内存设备自动跳模糊
- [ ] 列表滚动 60fps（背景是静态的，不应掉帧）
- [ ] 权限撤销（用户清除数据）后背景降级为 atmosphere 而非崩溃
