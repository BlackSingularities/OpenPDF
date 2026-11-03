package com.blacksingularities.openpdf.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Check

val Pill = RoundedCornerShape(50)

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = Type.body,
    color: Color = P.text,
    weight: FontWeight? = null,
    maxLines: Int = 1,
) {
    BasicText(
        text,
        modifier,
        style = style.copy(color = color, fontWeight = weight ?: style.fontWeight),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Podpowiedź po najechaniu (z opcjonalnym skrótem klawiszowym). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tip(text: String?, shortcut: String? = null, content: @Composable () -> Unit) {
    if (text == null) {
        content()
        return
    }
    TooltipArea(
        tooltip = {
            Row(
                Modifier.shadow(8.dp, RoundedCornerShape(8.dp)).background(P.raised2, RoundedCornerShape(8.dp))
                    .border(1.dp, P.line2, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Txt(text, style = Type.small)
                if (shortcut != null) Kbd(shortcut)
            }
        },
        delayMillis = 450,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(4.dp, 18.dp)),
    ) { content() }
}

@Composable
fun Kbd(text: String) {
    Box(Modifier.background(P.surface, RoundedCornerShape(5.dp)).border(1.dp, P.line2, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
        Txt(text, style = Type.mono.copy(fontSize = Type.tiny.fontSize), color = P.text2)
    }
}

enum class BtnKind { NORMAL, PRIMARY, GHOST }

/** Przycisk w kształcie pigułki, jak w OpenPano. */
@Composable
fun Btn(
    text: String?,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.NORMAL,
    active: Boolean = false,
    enabled: Boolean = true,
    tip: String? = null,
    shortcut: String? = null,
    height: Dp = 28.dp,
) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val p = P
    val (bg, fg, border) = when {
        active || kind == BtnKind.PRIMARY -> Triple(if (hovered && enabled) p.selHover else p.sel, p.selText, if (hovered && enabled) p.selHover else p.sel)
        kind == BtnKind.GHOST -> Triple(if (hovered && enabled) p.raised2 else Color.Transparent, if (hovered && enabled) p.text else p.text2, Color.Transparent)
        else -> Triple(if (hovered && enabled) p.raised2 else p.surface, p.text, if (hovered && enabled) p.text.copy(alpha = 0.45f) else p.line2)
    }
    Tip(tip, shortcut) {
        Row(
            modifier
                .height(height)
                .then(if (text == null) Modifier.width(height) else Modifier)
                .alpha(if (enabled) 1f else 0.42f)
                .clip(Pill)
                .background(bg)
                .border(1.dp, border, Pill)
                .hoverable(src, enabled)
                .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                .clickable(src, null, enabled = enabled, onClick = onClick)
                .padding(horizontal = if (text == null) 0.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(if (height < 26.dp) 14.dp else 16.dp), tint = fg)
            if (text != null) Txt(text, color = fg, weight = if (kind == BtnKind.PRIMARY || active) FontWeight.SemiBold else null)
        }
    }
}

@Composable
fun IconBtn(icon: ImageVector, tip: String?, onClick: () -> Unit, active: Boolean = false, enabled: Boolean = true, shortcut: String? = null, size: Dp = 28.dp) =
    Btn(null, icon, onClick, kind = if (active) BtnKind.NORMAL else BtnKind.GHOST, active = active, enabled = enabled, tip = tip, shortcut = shortcut, height = size)

data class SegItem<T>(val value: T, val label: String? = null, val icon: ImageVector? = null, val tip: String? = null, val shortcut: String? = null)

/** Przełącznik segmentowy w pigułce. */
@Composable
fun <T> Seg(items: List<SegItem<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, height: Dp = 28.dp, fill: Boolean = false) {
    Row(
        modifier.height(height).background(P.surface, Pill).border(1.dp, P.line, Pill).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (it in items) {
            val on = it.value == selected
            val src = remember { MutableInteractionSource() }
            val hovered by src.collectIsHoveredAsState()
            Box(if (fill) Modifier.weight(1f) else Modifier) {
                Tip(it.tip, it.shortcut) {
                    Row(
                        Modifier
                            .then(if (fill) Modifier.fillMaxWidth() else if (it.label == null) Modifier.width(height + 2.dp) else Modifier)
                            .height(height - 6.dp)
                            .clip(Pill)
                            .background(if (on) P.sel else if (hovered) P.text.copy(alpha = 0.06f) else Color.Transparent)
                            .hoverable(src)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(src, null) { onSelect(it.value) }
                            .padding(horizontal = if (it.label == null) 0.dp else 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    ) {
                        val fg = if (on) P.selText else if (hovered) P.text else P.text2
                        if (it.icon != null) Icon(it.icon, null, Modifier.size(15.dp), tint = fg)
                        if (it.label != null) Txt(it.label, color = fg, style = Type.small, weight = if (on) FontWeight.SemiBold else null)
                    }
                }
            }
        }
    }
}

@Composable
fun VDivider(height: Dp = 18.dp) = Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(height).background(P.line))

@Composable
fun HDivider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(P.line))

/** Przełącznik włącz/wyłącz. */
@Composable
fun Switch(on: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateFloatAsState(if (on) 1f else 0f)
    Box(
        Modifier.size(34.dp, 20.dp).clip(Pill).background(if (on) P.sel else P.raised2).border(1.dp, P.line2, Pill)
            .pointerHoverIcon(PointerIcon.Hand).clickable { onChange(!on) },
    ) {
        Box(
            Modifier.padding(start = (3 + 14 * x).dp, top = 3.dp).size(14.dp).background(if (on) P.selText else P.text2, CircleShape),
        )
    }
}

/** Pole tekstowe w pigułce. */
@Composable
fun Input(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leading: ImageVector? = null,
    password: Boolean = false,
    focus: FocusRequester? = null,
    onFocus: (Boolean) -> Unit = {},
    onEnter: (shift: Boolean) -> Unit = {},
    onEscape: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.height(30.dp).background(P.surface, Pill).border(1.dp, P.line2, Pill).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) Icon(leading, null, Modifier.size(15.dp), tint = P.text3)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Txt(placeholder, color = P.text3)
            BasicTextField(
                value, onChange,
                Modifier.fillMaxWidth()
                    .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                    .onFocusChanged { onFocus(it.isFocused) }
                    .onPreviewKeyEvent {
                        if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (it.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                onEnter(it.isShiftPressed)
                                true
                            }
                            Key.Escape -> onEscape?.let { e -> e(); true } ?: false
                            else -> false
                        }
                    },
                singleLine = true,
                textStyle = Type.body.copy(color = P.text),
                cursorBrush = SolidColor(P.text),
                visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions.Default,
                keyboardActions = KeyboardActions.Default,
            )
        }
        trailing?.invoke(this)
    }
}


/** Pozycja menu rozwijanego. */
@Composable
fun MenuItem(text: String, icon: ImageVector? = null, shortcut: String? = null, enabled: Boolean = true, checked: Boolean? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(8.dp))
            .background(if (hovered && enabled) P.raised2 else Color.Transparent)
            .hoverable(src, enabled)
            .clickable(src, null, enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            when {
                checked == true -> Icon(androidx.compose.material.icons.Icons.Rounded.Check, null, Modifier.size(15.dp), tint = P.text)
                icon != null -> Icon(icon, null, Modifier.size(15.dp), tint = P.text2)
            }
        }
        Txt(text, Modifier.weight(1f))
        if (shortcut != null) Txt(shortcut, style = Type.small, color = P.text3)
    }
}

@Composable
fun MenuSeparator() = Box(Modifier.padding(vertical = 4.dp, horizontal = 6.dp).fillMaxWidth().height(1.dp).background(P.line))

/** Położenie menu: pod przyciskiem (wyrównane do lewej albo prawej krawędzi), a gdy brak miejsca – nad nim. */
private class BelowAnchor(private val gap: Int, private val alignEnd: Boolean) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        var x = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left
        x = x.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        var y = anchorBounds.bottom + gap
        if (y + popupContentSize.height > windowSize.height) y = maxOf(0, anchorBounds.top - gap - popupContentSize.height)
        return IntOffset(x, y)
    }
}

/** Menu w okienku pod przyciskiem (rodzicem jest pudełko z przyciskiem). */
@Composable
fun Menu(open: Boolean, onDismiss: () -> Unit, alignEnd: Boolean = true, width: Dp = 250.dp, content: @Composable ColumnScope.() -> Unit) {
    if (!open) return
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    val provider = remember(gap, alignEnd) { BelowAnchor(gap, alignEnd) }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        // Esc zamyka okienko (dismissOnBackPress).
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier.width(width).shadow(18.dp, RoundedCornerShape(12.dp)).background(P.raised, RoundedCornerShape(12.dp))
                .border(1.dp, P.line2, RoundedCornerShape(12.dp)).padding(5.dp),
            content = content,
        )
    }
}

/** Okno dialogowe na przyciemnionym tle. */
@Composable
fun Modal(onDismiss: () -> Unit, width: Dp = 460.dp, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = width).fillMaxWidth(0.92f).heightIn(max = 760.dp)
                .shadow(30.dp, RoundedCornerShape(18.dp)).background(P.panel, RoundedCornerShape(18.dp))
                .border(1.dp, P.line2, RoundedCornerShape(18.dp))
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(20.dp),
            content = content,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Txt(text.uppercase(), modifier.padding(top = 6.dp, bottom = 6.dp), style = Type.tiny, color = P.text3, weight = FontWeight.SemiBold)


@Composable
fun RowScope.Fill() = Spacer(Modifier.weight(1f))
