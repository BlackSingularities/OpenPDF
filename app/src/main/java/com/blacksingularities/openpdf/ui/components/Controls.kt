package com.blacksingularities.openpdf.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blacksingularities.openpdf.ui.theme.BlockShape
import com.blacksingularities.openpdf.ui.theme.GlassIconButton
import com.blacksingularities.openpdf.ui.theme.GlassLevel
import com.blacksingularities.openpdf.ui.theme.Palette
import com.blacksingularities.openpdf.ui.theme.PickerOption
import com.blacksingularities.openpdf.ui.theme.glass
import com.blacksingularities.openpdf.ui.theme.reactive

/** Okrągły przycisk z ikoną (jak w OpenShot), bez obrotu. */
@Composable
fun RoundIcon(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    badge: String? = null,
    size: Dp = 44.dp,
    options: List<PickerOption>? = null,
    selected: Int = -1,
    onPick: (Int) -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    GlassIconButton(
        icon = icon,
        label = label,
        rotation = 0f,
        modifier = modifier,
        size = size,
        active = active,
        enabled = enabled,
        badge = badge,
        onLongPress = onLongPress,
        options = options,
        selected = selected,
        onPick = onPick,
        onClick = onClick,
    )
}

/**
 * Panel z nagłówkiem i przyciskiem zamknięcia: na wąskim ekranie – na całą wysokość od dołu,
 * na szerokim – kolumna przy prawej krawędzi (dokument zostaje widoczny).
 */
@Composable
fun Panel(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (wide) 0.25f else 0f))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
        Column(
            modifier
                .then(if (wide) Modifier.align(Alignment.CenterEnd).width(420.dp).fillMaxHeight() else Modifier.fillMaxSize())
                .background(GlassLevel.Thick.tint)
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                actions()
                Spacer(Modifier.width(6.dp))
                GlassIconButton(Icons.Rounded.Close, "Zamknij", 0f, size = 38.dp, onClick = onClose)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
fun Group(
    title: String?,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    Column {
        if (title != null) Row(
            Modifier
                .fillMaxWidth()
                .then(if (collapsible) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(start = 4.dp, end = 4.dp, bottom = 3.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.uppercase(),
                color = Palette.Amber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.weight(1f),
            )
            if (collapsible) {
                Icon(
                    Icons.Rounded.ExpandMore,
                    if (expanded) "Zwiń sekcję $title" else "Rozwiń sekcję $title",
                    tint = Palette.Amber,
                    modifier = Modifier.size(18.dp).rotate(if (expanded) 0f else -90f),
                )
            }
        }
        AnimatedVisibility(visible = !collapsible || expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(vertical = 2.dp),
                content = content,
            )
        }
    }
}

@Composable
fun RowIcon(icon: ImageVector, tint: Color = Palette.Amber) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!value) }
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 13.sp, color = if (enabled) Color.White else Palette.TextFaint)
            if (subtitle != null) Text(subtitle, fontSize = 10.sp, color = Palette.TextDim, lineHeight = 12.sp)
        }
        Switch(
            checked = value,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.scale(0.82f),
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Amber, checkedThumbColor = Palette.Ink),
        )
    }
}

/** Wybór w jednej linii z przełącznikiem segmentowym po prawej. */
@Composable
fun <T> CompactChoice(
    rowIcon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    icon: ((T) -> ImageVector)? = null,
    short: (T) -> String = label,
    hint: String? = null,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(rowIcon)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                hint?.let { "${label(selected)} · $it" } ?: label(selected),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = Palette.TextDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(2.dp),
        ) {
            options.forEach { o ->
                val sel = o == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Color.White else Color.Transparent)
                        .selectable(selected = sel, role = Role.RadioButton, onClick = { onSelect(o) })
                        .padding(horizontal = if (icon != null) 10.dp else 9.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) {
                        Icon(icon(o), label(o), tint = if (sel) Palette.Ink else Palette.TextDim, modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            short(o),
                            fontSize = 11.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            color = if (sel) Palette.Ink else Palette.TextDim,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun <T> Chips(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    enabled: Boolean = true,
    hint: String? = null,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon)
            Text(title, fontSize = 14.sp, color = if (enabled) Color.White else Palette.TextFaint, modifier = Modifier.padding(horizontal = 12.dp))
        }
        Row(
            Modifier
                .padding(start = 40.dp, top = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            options.forEach { o ->
                val sel = o == selected
                Text(
                    label(o),
                    color = if (sel) Palette.Ink else if (enabled) Color.White else Palette.TextFaint,
                    fontSize = 12.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Palette.Amber else Color.White.copy(alpha = 0.07f))
                        .clickable(enabled = enabled) { onSelect(o) }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
        if (hint != null) Text(hint, fontSize = 11.sp, color = Palette.TextDim, modifier = Modifier.padding(start = 40.dp, top = 3.dp))
    }
}

/** Wiersz-akcja: ikona, tytuł, opis; dotknięcie wykonuje akcję. */
@Composable
fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, if (danger) Palette.Record else Palette.Amber)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title,
                fontSize = 14.sp,
                color = when {
                    !enabled -> Palette.TextFaint
                    danger -> Palette.Record
                    else -> Color.White
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = Palette.TextDim, lineHeight = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
fun InfoRow(icon: ImageVector, title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 11.sp, color = Palette.TextDim)
            Text(value.ifBlank { "—" }, fontSize = 13.sp, color = Color.White)
        }
    }
}

/** Przycisk-pigułka: biały (główny) albo półprzezroczysty. */
@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = when {
        !enabled -> Color.White.copy(alpha = 0.08f)
        danger -> Palette.Record
        primary -> Palette.Amber
        else -> Color.White.copy(alpha = 0.1f)
    }
    val fg = when {
        !enabled -> Palette.TextFaint
        danger -> Color.White
        primary -> Palette.Ink
        else -> Color.White
    }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

/** Ciemne okno dialogowe w stylu aplikacji. */
@Composable
fun DarkDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.PanelHigh)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                buttons()
            }
        }
    }
}

@Composable
fun DarkField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    password: Boolean = false,
    number: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                password -> KeyboardType.Password
                number -> KeyboardType.Number
                else -> KeyboardType.Text
            },
        ),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
            focusedLabelColor = Color.White,
            unfocusedLabelColor = Palette.TextDim,
            cursorColor = Color.White,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
        ),
    )
}

/** Zakładka-chip (jak w ustawieniach OpenShot). */
@Composable
fun TabChip(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .reactive(label, onClick = onClick)
            .glass(BlockShape, active = selected)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Palette.Ink else Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = if (selected) Palette.Ink else Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

/** Kropka koloru do wyboru. */
@Composable
fun ColorDot(color: Int, selected: Boolean, size: Dp = 30.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(if (selected) 3.dp else 2.dp)
            .clip(CircleShape)
            .background(Color(color)),
    )
}

/** Odstęp pionowy. */
@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
