# H7 按键拦截采用应用内无障碍服务而非 LSPosed 或 keylayout 改造

dagu（Xiaomi Pad 5 Pro 12.4）的 `himax-stylus.kl` 把 H7 笔按键（scancode 331/332）映射为 PAGE_UP/PAGE_DOWN 键事件——这是为小米笔记兼容性而做的既有决定，不可改回 BTN_STYLUS（HyperOS `PhoneWindowManager.interceptKeyBeforeQueueing` 会吞掉 308/309）。DrawAnywhere 的悬浮窗均为 `FLAG_NOT_FOCUSABLE`，永远收不到键事件，因此标准的 `MotionEvent.buttonState` 笔按钮路径在此设备上不存在。

决定：在 DrawAnywhere 内新增 `AccessibilityService`（`flagRequestFilterKeyEvents`）作为按键过滤器：画布可见且非透传时拦截 92/93 并转为应用动作，否则放行。代价是需要用户在系统设置中启用一次无障碍服务。

否决的替代方案：扩展 MaxMiPadInput LSPosed 模块做拦截转发——免无障碍授权，但让两个独立项目耦合、模块更新需同步维护；改 keylayout——已被先前验证否定。
