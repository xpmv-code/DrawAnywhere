# DrawAnywhere H7 定制

DrawAnywhere（屏幕任意处绘图的安卓悬浮窗应用）针对 Xiaomi Pad 5 Pro 12.4（dagu / HyperOS 2）与 CHUWI H7 触控笔（`himax-stylus`）的定制版。H7 的两个物理按键经 keylayout 映射为 PAGE_UP/PAGE_DOWN 键事件，而非标准 Android 笔按钮信号。

## Language

**临时橡皮（Hold-Eraser）**:
按住 H7 任一物理按键期间，笔迹作为橡皮擦除；笔画进行中按下即时切换（已画轨迹保留），松开恢复原工具。
_Avoid_: 按键橡皮、按钮模式、临时擦除态

**H7 按键**:
H7 笔身两个物理按键：上键（scancode 331 / PAGE_DOWN）、下键（scancode 332 / PAGE_UP）。经无障碍按键过滤进入本应用。
_Avoid_: 笔按钮（buttonState 语义在 dagu 上不存在，误导）

**上键单击（Upper Tap）**:
快速按下并松开上键且笔尖未触屏——在笔和橡皮两个"侧面"间切换：非橡皮工具 → 切到橡皮；橡皮 → 恢复进入橡皮前的工具。
_Avoid_: 双击、翻转

**下键单击（Lower Tap）**:
快速按下并松开下键且笔尖未触屏——按固定顺序循环切换全部 6 种工具：Pen → Laser → Rectangle → Ellipse → StrokeEraser → PixelEraser → Pen（与工具栏顺序一致）。
_Avoid_: 滚动、翻页

**逐点压感（Per-Point Pressure）**:
笔迹沿轨迹按当时压力连续变宽（真笔锋效果），区别于"落笔压力定整笔宽度"。压力来自 H7 的 ABS_PRESSURE（0–4095，归一化 0–1）。
_Avoid_: 压感宽度（粒度不明）、笔压

**按键归属（Key Interception）**:
画布可见且非透传时，H7 按键归本应用（拦截）；画布隐藏或透传时放行给系统（小米笔记翻页等原行为不受影响）。
_Avoid_: 抢占、全局接管

**按键过滤器（Key Filter）**:
本应用内置的无障碍服务，全局监听键事件并按"按键归属"规则拦截或放行 H7 按键（见 docs/adr/0001）。
_Avoid_: 键盘钩子、全局监听器

**按键长按（Hold）**:
见"临时橡皮"。长按超过单击判定窗口后松开且笔尖未触屏 → 无动作（单击不成立）。
