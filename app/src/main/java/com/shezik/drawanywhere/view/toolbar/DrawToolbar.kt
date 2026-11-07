package com.shezik.drawanywhere.view.toolbar

import android.content.Context
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shezik.drawanywhere.DrawViewModel
import com.shezik.drawanywhere.util.scrollFadingEdges
import com.shezik.drawanywhere.ui.theme.DrawAnywhereTheme
import com.shezik.drawanywhere.ui.theme.Spacing

enum class ToolbarOrientation { HORIZONTAL, VERTICAL }

@Composable
fun DrawToolbar(
    viewModel: DrawViewModel,
    modifier: Modifier = Modifier,
    onNaturalSizeChanged: (widthPx: Int, heightPx: Int) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val canClearCanvas by viewModel.canClearCanvas.collectAsState()
    val lockMode by viewModel.lockMode.collectAsState()

    val haptics = LocalHapticFeedback.current
    val hScrollState = rememberScrollState()
    val vScrollState = rememberScrollState()

    val allButtonsMap = createAllToolbarButtons(
        uiState = uiState,
        canUndo = canUndo,
        canRedo = canRedo,
        canClearCanvas = canClearCanvas,
        onCanvasVisibilityToggle = viewModel::toggleCanvasVisibility,
        onCanvasPassthroughToggle = viewModel::toggleCanvasPassthrough,
        onClearCanvas = viewModel::clearCanvas,
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onPenTypeSwitch = viewModel::switchToPen,
        onColorChange = viewModel::setPenColor,
        onPresetColorChange = viewModel::setPresetColor,
        onStrokeWidthChange = viewModel::setStrokeWidth,
        onAlphaChange = viewModel::setStrokeAlpha,
        onChangeOrientation = viewModel::setToolbarOrientation,
        onChangeAutoClearCanvas = viewModel::setAutoClearCanvas,
        onChangeVisibleOnStart = viewModel::setVisibleOnStart,
        fingerDrawingEnabled = uiState.fingerDrawingEnabled,
        onChangeFingerDrawingEnabled = viewModel::setFingerDrawingEnabled,
        pressureEnabled = uiState.pressureEnabled,
        onChangePressureEnabled = viewModel::setPressureEnabled,
        onCycleLockMode = viewModel::cycleLockMode,
        lockMode = lockMode,
        onQuitApplication = viewModel::quitApplication
    ).associateBy { it.id }

    DrawAnywhereTheme {
        // WRAP_CONTENT overlay windows report unreliable incoming constraints on
        // some OEM skins (HyperOS caps at a stale frame); use the real display
        // metrics as the size ceiling instead.
        val density = LocalDensity.current
        val windowManager = LocalContext.current.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val screenBounds = remember { windowManager?.maximumWindowMetrics?.bounds }
        val maxW = screenBounds?.let { with(density) { it.width().toDp() } }
        val maxH = screenBounds?.let { with(density) { it.height().toDp() } }
        var reportedSize by remember { mutableStateOf(IntSize.Zero) }
        BoxWithConstraints(
            modifier = Modifier.layout { measurable, _ ->
                // Overlay windows cap wrap-content measurement (HyperOS clamps at a
                // stale 1160px frame); measure unbounded and report the natural size
                // upward so the service can size the window explicitly.
                val placeable = measurable.measure(Constraints())
                val natural = IntSize(placeable.width, placeable.height)
                if (natural != reportedSize) {
                    reportedSize = natural
                    onNaturalSizeChanged(natural.width, natural.height)
                }
                layout(natural.width, natural.height) { placeable.place(0, 0) }
            }
        ) {
            DraggableToolbarCard(
                modifier = modifier
                    .wrapContentSize(unbounded = true)
                    .then(maxW?.let { Modifier.widthIn(max = it) } ?: Modifier.widthIn(max = maxWidth))
                    .then(maxH?.let { Modifier.heightIn(max = it) } ?: Modifier.heightIn(max = maxHeight))
                    .scrollFadingEdges(hScrollState, false)
                    .scrollFadingEdges(vScrollState, true)
                    .horizontalScroll(hScrollState)
                    .verticalScroll(vScrollState)
                    .padding(Spacing.xs),
                haptics = haptics,
                onPositionChange = viewModel::updateToolbarPosition,
                onPositionSaved = viewModel::saveToolbarPosition,
                onToolbarInteracted = viewModel::resetToolbarTimer,
                onDragStart = viewModel::onDismissDragStart,
                onDragPosition = viewModel::onDismissDragMove,
                onDragEnd = viewModel::onDismissDragEnd,
            ) {
                ToolbarButtonsContainer(
                    modifier = Modifier.padding(Spacing.sm),
                    uiState = uiState,
                    allButtonsMap = allButtonsMap,
                    onExpandToggleClick = viewModel::toggleSecondDrawer
                )
            }
        }
    }
}

@Composable
internal fun ToolbarButtonsContainer(
    modifier: Modifier = Modifier,
    uiState: com.shezik.drawanywhere.UiState,
    allButtonsMap: Map<String, ToolbarButton>,
    onExpandToggleClick: () -> Unit
) {
    val orientation = uiState.toolbarOrientation
    val isFirstDrawerOpen = uiState.firstDrawerOpen
    val isSecondDrawerOpen = uiState.secondDrawerOpen
    val firstDrawerButtonIds = uiState.firstDrawerButtons
    val secondDrawerButtonIds = uiState.secondDrawerButtons
    val secondDrawerPinnedButtons = uiState.secondDrawerPinnedButtons

    val standaloneButtonIds = allButtonsMap.keys.filter {
        it !in firstDrawerButtonIds && it !in secondDrawerButtonIds
    }
    val arrangement = Arrangement.spacedBy(Spacing.sm)
    val popupAlignment = when (orientation) {
        ToolbarOrientation.HORIZONTAL -> Alignment.TopCenter
        ToolbarOrientation.VERTICAL -> Alignment.CenterEnd
    }
    val animMod = Modifier.animateContentSize(
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
    )
    val isDividerVisible = isFirstDrawerOpen &&
        (secondDrawerPinnedButtons.isNotEmpty() || (isSecondDrawerOpen && secondDrawerButtonIds.isNotEmpty()))
    val isExpandButtonVisible = isFirstDrawerOpen && secondDrawerButtonIds.isNotEmpty()

    val isHorizontal = orientation == ToolbarOrientation.HORIZONTAL
    val toolButtonIds = firstDrawerButtonIds.filter { it.startsWith("tool_") }
    val otherFirstDrawerIds = firstDrawerButtonIds.filter { !it.startsWith("tool_") }

    @Composable
    fun ToolGrid() {
        if (isHorizontal) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                toolButtonIds.forEach { id ->
                    allButtonsMap[id]?.let { RenderButton(it, popupAlignment) }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                toolButtonIds.chunked(2).forEach { rowIds ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        rowIds.forEach { id ->
                            allButtonsMap[id]?.let { RenderButton(it, popupAlignment) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ToolbarContent() {
        standaloneButtonIds.forEach { id -> allButtonsMap[id]?.let { RenderButton(it, popupAlignment) } }
        AnimatedVisibility(isFirstDrawerOpen,
            enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.5f),
            exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.5f)
        ) { ToolGrid() }
        otherFirstDrawerIds.forEach { id ->
            allButtonsMap[id]?.let { btn ->
                AnimatedVisibility(isFirstDrawerOpen,
                    enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.5f),
                    exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.5f)
                ) { RenderButton(btn, popupAlignment) }
            }
        }
        AnimatedVisibility(isDividerVisible, enter = fadeIn(tween(300)), exit = fadeOut(tween(300))) {
            if (isHorizontal) VerticalDivider(
                Modifier.height(24.dp).padding(horizontal = Spacing.sm), thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant)
            else HorizontalDivider(
                Modifier.width(24.dp).padding(vertical = Spacing.sm), thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant)
        }
        secondDrawerButtonIds.forEach { id ->
            allButtonsMap[id]?.let { btn ->
                val visible = isFirstDrawerOpen && (isSecondDrawerOpen || id in secondDrawerPinnedButtons)
                AnimatedVisibility(visible,
                    enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.5f),
                    exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.5f)
                ) { RenderButton(btn, popupAlignment) }
            }
        }
        AnimatedVisibility(isExpandButtonVisible,
            enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.5f),
            exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.5f)
        ) { ToolbarExpandButton(Modifier, isSecondDrawerOpen, onExpandToggleClick, orientation) }
    }

    if (isHorizontal) {
        Row(modifier = modifier.then(animMod), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = arrangement) {
            ToolbarContent()
        }
    } else {
        Column(modifier = modifier.then(animMod), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = arrangement) {
            ToolbarContent()
        }
    }
}
