# ShopMall 三端 UI 设计规范（现代玻璃拟态风）

> 日期：2026-06-30
> 状态：已确认
> 范围：web-user / web-merchant / web-admin 三端统一设计语言
> 风格：浅色渐变玻璃拟态（Glassmorphism）+ 翡翠青绿渐变

---

## 一、设计决策总结

| 决策项 | 选择 | 说明 |
|--------|------|------|
| 视觉美学 | 浅色渐变玻璃风 | 参考 Stripe / Apple / Linear，柔和渐变光晕 + 毛玻璃卡片 |
| 主色调 | 翡翠青绿渐变 | `#10B981`(翡翠) → `#06B6D4`(青蓝)，现代科技感 |
| 字体 | Sora(标题) + Manrope(正文) | 现代几何无衬线，避免 Inter 等通用字体 |
| 交付形式 | HTML 交互原型 + 设计规范文档 | 沿用项目 prototype-web-*.html 惯例 |
| 交付范围 | 三端各 3-4 个核心页面 | 共 10 个页面，每端聚焦关键流程 |
| 动效 | 按钮动画 + 页面过渡 + 卡片悬浮 | 全局微动效，优雅不浮夸 |

---

## 二、色彩系统

### 2.1 品牌渐变

```css
/* 主品牌渐变 - 翡翠→青蓝 */
--gradient-brand: linear-gradient(135deg, #10B981 0%, #06B6D4 100%);
--gradient-brand-hover: linear-gradient(135deg, #059669 0%, #0891B2 100%);
--gradient-brand-soft: linear-gradient(135deg, rgba(16,185,129,0.1) 0%, rgba(6,182,212,0.1) 100%);

/* 氛围渐变光晕（mesh gradient 背景） */
--gradient-mesh:
  radial-gradient(at 0% 0%, rgba(16,185,129,0.12) 0px, transparent 50%),
  radial-gradient(at 98% 2%, rgba(6,182,212,0.12) 0px, transparent 50%),
  radial-gradient(at 50% 98%, rgba(16,185,129,0.08) 0px, transparent 50%);
```

### 2.2 中性色（浅色基调）

| 变量 | 值 | 用途 |
|------|------|------|
| `--color-bg` | `#F8FAFC` | 页面背景 |
| `--color-surface` | `#FFFFFF` | 卡片背景 |
| `--color-glass` | `rgba(255,255,255,0.65)` | 玻璃卡片 |
| `--color-glass-strong` | `rgba(255,255,255,0.85)` | 强玻璃卡片 |
| `--color-glass-border` | `rgba(255,255,255,0.6)` | 玻璃边框 |
| `--color-text` | `#0F172A` | 主文字 |
| `--color-text-secondary` | `#475569` | 次文字 |
| `--color-text-muted` | `#94A3B8` | 弱文字 |
| `--color-border` | `rgba(226,232,240,0.7)` | 分割线 |

### 2.3 品牌色与状态色

```css
--color-emerald: #10B981;   --color-emerald-light: #34D399;  --color-emerald-dark: #059669;
--color-cyan: #06B6D4;       --color-cyan-light: #22D3EE;      --color-cyan-dark: #0891B2;
--color-success: #22C55E;    --color-warning: #F59E0B;         --color-error: #EF4444;  --color-info: #3B82F6;
```

---

## 三、字体系统

```css
/* 标题字体：Sora - 现代几何无衬线，具辨识度 */
--font-display: 'Sora', 'PingFang SC', 'Noto Sans SC', sans-serif;

/* 正文字体：Manrope - 清晰友好，可读性强 */
--font-body: 'Manrope', 'PingFang SC', 'Microsoft YaHei', sans-serif;
```

| 字号阶梯 | 值 | 用途 |
|----------|------|------|
| hero | 56px | Hero 大标题 |
| h1 | 40px | 页面主标题 |
| h2 | 28px | 区块标题 |
| h3 | 20px | 卡片标题 |
| body | 16px | 正文 |
| small | 14px | 辅助文字 |
| caption | 12px | 标签/说明 |

---

## 四、间距 / 圆角 / 阴影

```css
/* 间距（8px 基准） */
--space-xs: 4px; --space-sm: 8px; --space-md: 16px;
--space-lg: 24px; --space-xl: 40px; --space-2xl: 64px;

/* 圆角 */
--radius-sm: 8px; --radius-md: 12px; --radius-lg: 16px;
--radius-xl: 24px; --radius-pill: 999px;

/* 阴影 */
--shadow-sm: 0 2px 8px rgba(15,23,42,0.05);
--shadow-md: 0 4px 16px rgba(15,23,42,0.07);
--shadow-lg: 0 12px 32px rgba(15,23,42,0.1);
--shadow-glow: 0 0 24px rgba(16,185,129,0.35);

/* 缓动 */
--ease-out: cubic-bezier(0.16,1,0.3,1);
--ease-spring: cubic-bezier(0.34,1.56,0.64,1);
--transition: 0.3s var(--ease-out);
```

---

## 五、组件规范

### 5.1 按钮

| 类型 | 样式 | 动效 |
|------|------|------|
| 主按钮 | 渐变背景 + 白字 + 圆角12px + 阴影 | hover 上浮2px + 光晕扩散 + 光泽掠过；点击波纹 |
| 次按钮 | 玻璃白底 + 渐变描边 | hover 描边加粗 + 背景变亮 |
| 文字按钮 | 无背景 + 渐变文字色 | hover 文字加粗 + 下划线滑入 |
| 图标按钮 | 圆形玻璃底 | hover 旋转/缩放 + 光晕 |

### 5.2 卡片

- **玻璃卡片**：`backdrop-filter: blur(20px) saturate(180%)` + 半透明白 + 1px 浅边框
- **hover**：上浮 4px + 阴影加深 + 图片缩放 1.05
- **圆角**：16px

### 5.3 输入框

- 浅灰底 + focus 时渐变描边动画 + 轻微发光
- 圆角 12px

### 5.4 导航

- 玻璃 sticky 顶栏，滚动时模糊度增加
- 侧边栏（管理端/商家端）：玻璃背景 + 渐变 active 指示器

### 5.5 状态徽章

- 成功：浅绿底 + 绿字
- 警告：浅橙底 + 橙字
- 错误：浅红底 + 红字
- 进行中：渐变底 + 白字

---

## 六、动效规范

| 动效 | 触发 | 实现 | 时长 |
|------|------|------|------|
| 按钮光晕扩散 | hover | box-shadow 扩散 | 0.3s |
| 按钮光泽掠过 | hover | 伪元素 translateX | 0.6s |
| 按钮点击波纹 | click | JS 生成涟漪元素 | 0.6s |
| 卡片悬浮 | hover | translateY(-4px) + shadow | 0.3s |
| 图片缩放 | hover | transform scale(1.05) | 0.4s |
| 页面切换 | 路由切换 | opacity + translateY 淡入上滑 | 0.4s |
| 数字滚动 | 数据加载 | JS 数字递增动画 | 1.2s |
| 骨架流光 | 加载中 | shimmer 关键帧 | 1.5s 循环 |
| Tab 指示器 | 切换 | transform translateX 滑动 | 0.3s |
| 弹窗淡入 | 打开 | scale + opacity | 0.3s spring |
| 滚动视差 | 滚动 | transform translateY | 跟随滚动 |

---

## 七、三端页面清单

### 7.1 用户端（4 页）- `prototype-web-user-v3.html`

1. **首页** - 渐变 Hero + 分类入口 + 精选商品网格 + 秒杀专区 + 品牌承诺
2. **商品详情页** - 玻璃图片区 + 详情 + SKU选择 + 评价
3. **购物车页** - 玻璃卡片列表 + 浮动结算栏
4. **个人中心** - 用户信息 + 订单状态快捷入口

### 7.2 商家端（3 页）- `prototype-web-merchant-v3.html`

1. **数据看板** - 渐变数据卡片 + 图表 + 实时订单
2. **商品管理** - 玻璃表格 + 状态筛选 + 操作
3. **订单管理** - 订单列表 + 状态时间线

### 7.3 管理端（3 页）- `prototype-web-admin-v3.html`

1. **运营看板** - 全局数据 + 趋势图 + 排行榜
2. **商家审核** - 审核列表 + 详情抽屉
3. **系统管理** - 角色/权限/用户管理

---

## 八、设计原则

1. **玻璃质感统一**：所有卡片、导航、弹窗使用毛玻璃效果，营造层次感
2. **渐变点缀**：翡翠青绿渐变用于主按钮、强调元素、数据可视化，不滥用
3. **大量留白**：区块间距 40-64px，呼吸感强
4. **微动效**：hover/scroll 触发平滑过渡（0.3s ease-out），不浮夸
5. **柔和阴影**：阴影色用 slate 系（rgba(15,23,42)），不用纯黑
6. **图片优先**：商品大图展示，文字辅助
7. **响应式**：原型以桌面端为主（1280px+），兼顾移动端适配
