package com.blacksingularities.openpdf.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt


/**
 * Rozmycie tylko tam, gdzie jest włączone (górne przyciski). Pozostałe elementy mają
 * zwykłe półprzezroczyste ciemne tło.
 */
val LocalBlur = staticCompositionLocalOf { false }

/** Czy wykonywać haptykę przy dotyku elementów interfejsu. */
val LocalHaptics = staticCompositionLocalOf { true }

/** Wewnątrz szklanego paska narzędzi przyciski są płaskie (bez własnego szkła). */
val LocalToolbar = staticCompositionLocalOf { false }

/**
 * Przełączniki stanu (lampa, format, klatkaż…) pokazują dymek z nową wartością także po
 * zwykłym dotknięciu – nie tylko po przytrzymaniu.
 */
val LocalHintOnTap = staticCompositionLocalOf { false }

/**
 * Główny interfejs aparatu: bez żadnych teł – same ikony i napisy na obrazie.
 * Aktywny element to biała ikona z kropką pod spodem. Ustawienia i wyskakujące listy mają tło.
 */
val LocalNoBackground = staticCompositionLocalOf { false }

/** Kolor treści aktywnego elementu: czarny na białym bloku albo biały, gdy nie ma teł. */
@Composable
fun activeContent(): Color = if (LocalNoBackground.current) Color.White else Palette.Ink

/** Dymki z nazwą elementu pokazywane po przytrzymaniu. */
val LocalHints = staticCompositionLocalOf<HintState?> { null }

/**
 * Płaskie, nieprzezroczyste tła (czarno-biały interfejs, bez rozmycia – lekki dla GPU).
 * [tint] – kolor tła elementu.
 */
enum class GlassLevel(val tint: Color) {
    /** Dolny panel sterowania. */
    Faint(Color(0xFF000000)),
    /** Przyciski i plakietki. */
    Thin(Color(0xFF1C1C1C)),
    /** Paski i panele. */
    Regular(Color(0xFF000000)),
    /** Arkusz ustawień. */
    Thick(Color(0xFF000000)),
}

/** Aktywny element: biały blok z czarną ikoną. */
private val ActiveTint = Color.White

/** Kształt przycisków i plakietek: koło / pigułka. */
val BlockShape = RoundedCornerShape(50)

/** Płaskie tło elementu (biel dla aktywnych). Nazwa zachowana dla zgodności wywołań. */
@Composable
fun Modifier.glass(
    shape: Shape,
    level: GlassLevel = GlassLevel.Thin,
    active: Boolean = false,
    border: Boolean = true,
): Modifier {
    if (LocalNoBackground.current) return this.clip(shape)
    val bg by animateColorAsState(if (active) ActiveTint else level.tint, tween(160), label = "flatBg")
    return this.clip(shape).background(bg)
}

/**
 * Obrót treści razem z układem: przy obrocie o 90°/270° szerokość i wysokość się zamieniają,
 * więc tekst (np. „+0,0 EV”) biegnie pionowo w całości, a pole dopasowuje się do niego.
 */
fun Modifier.rotatedLayout(degrees: Float): Modifier = this
    .layout { measurable, constraints ->
        val quarter = (((degrees / 90f).roundToInt() % 4) + 4) % 4
        val swap = quarter % 2 == 1
        val c = if (swap) {
            androidx.compose.ui.unit.Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            )
        } else {
            constraints
        }
        val p = measurable.measure(c)
        val w = if (swap) p.height else p.width
        val h = if (swap) p.width else p.height
        layout(w, h) { p.place((w - p.width) / 2, (h - p.height) / 2) }
    }
    .graphicsLayer { rotationZ = degrees }

/** Rozkłada elementy równo na całą szerokość; gdy się nie mieszczą – ciasno, odstęp 2 dp. */
object SpreadOrPack : androidx.compose.foundation.layout.Arrangement.Horizontal {
    override val spacing = 2.dp

    override fun androidx.compose.ui.unit.Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        outPositions: IntArray,
    ) {
        val minGap = spacing.roundToPx()
        val content = sizes.sum()
        val gap = if (sizes.size > 1) maxOf(minGap, (totalSize - content) / (sizes.size - 1)) else 0
        var x = if (sizes.size == 1) (totalSize - content) / 2 else 0
        sizes.forEachIndexed { i, w ->
            outPositions[i] = x
            x += w + gap
        }
    }
}

/** Stan dymków: tekst i prostokąt elementu (w układzie okna), przy którym dymek się pojawia. */
@Stable
class HintState {
    var text by mutableStateOf<String?>(null)
        private set
    var anchor by mutableStateOf(Rect.Zero)
        private set
    var token by mutableStateOf(0)
        private set

    fun show(text: String, anchor: Rect) {
        this.text = text
        this.anchor = anchor
        token++
    }

    fun hide() {
        text = null
    }
}

/** Opcja rozwijanej listy: ikona albo krótka wartość + podpis; [label] – pełna nazwa (dostępność). */
data class PickerOption(
    val label: String,
    val icon: ImageVector? = null,
    val value: String? = null,
    val caption: String? = null,
)

private val PICKER_ITEM = 50.dp
private val PICKER_STEP = 52.dp
private val PICKER_GAP = 8.dp

/**
 * Rozwijana lista opcji przycisku: przytrzymaj przycisk i przeciągnij w dół (w pionowym
 * pasku – w bok), przesuń palec na opcję i puść. Lista rysowana w warstwie nad interfejsem.
 */
@Stable
class PickerState {
    var options by mutableStateOf<List<PickerOption>>(emptyList())
        private set
    var anchor by mutableStateOf(Rect.Zero)
        private set
    var selected by mutableStateOf(-1)
        private set
    var highlighted by mutableStateOf(-1)
        private set
    var direction by mutableStateOf(PickerDirection.DOWN)
        private set
    val horizontal: Boolean get() = direction == PickerDirection.RIGHT || direction == PickerDirection.LEFT
    var visible by mutableStateOf(false)
        private set
    var rotation by mutableStateOf(0f)
        private set

    /** Lista otwarta zwykłym dotknięciem – zostaje na ekranie, opcję wybiera się dotknięciem. */
    var tapMode by mutableStateOf(false)
        private set
    private var tapPick: ((Int) -> Unit)? = null

    fun openTap(options: List<PickerOption>, selected: Int, anchor: Rect, direction: PickerDirection, onPick: (Int) -> Unit) {
        open(options, selected, anchor, direction, 0f)
        tapMode = true
        tapPick = onPick
    }

    fun pickTap(i: Int) {
        val f = tapPick
        close()
        f?.invoke(i)
    }

    fun open(options: List<PickerOption>, selected: Int, anchor: Rect, direction: PickerDirection, rotation: Float) {
        this.options = options
        this.selected = selected
        this.anchor = anchor
        this.direction = direction
        this.rotation = rotation
        highlighted = -1
        tapMode = false
        visible = true
    }

    /** Ustawia podświetlenie wg pozycji palca (w układzie okna); zwraca true, gdy się zmieniło. */
    fun track(root: Offset, density: androidx.compose.ui.unit.Density): Boolean {
        val step = with(density) { PICKER_STEP.toPx() }
        val gap = with(density) { PICKER_GAP.toPx() }
        val along = when (direction) {
            PickerDirection.DOWN -> root.y - (anchor.bottom + gap)
            PickerDirection.UP -> (anchor.top - gap) - root.y
            PickerDirection.RIGHT -> root.x - (anchor.right + gap)
            PickerDirection.LEFT -> (anchor.left - gap) - root.x
        }
        val i = if (along < 0f) -1 else (along / step).toInt().takeIf { it < options.size } ?: (options.size - 1)
        if (i == highlighted) return false
        highlighted = i
        return true
    }

    fun close() {
        visible = false
        tapMode = false
        tapPick = null
    }
}

val LocalPicker = staticCompositionLocalOf<PickerState?> { null }

/**
 * Przyciski z listą opcji: dotknięcie pokazuje wszystkie opcje, przytrzymanie wykonuje szybką
 * akcję (np. przełącza tryb). Bez tego dotknięcie przełącza, a lista rozwija się po przeciągnięciu.
 */
val LocalTapOpensPicker = staticCompositionLocalOf { false }

/** Kierunek, w którym rozwija się lista opcji (i w który trzeba przeciągnąć palec). */
enum class PickerDirection { DOWN, UP, RIGHT, LEFT }

/** Pasek u góry – w dół, u dołu – w górę, pionowy po lewej – w prawo, po prawej – w lewo. */
val LocalPickerDirection = staticCompositionLocalOf { PickerDirection.DOWN }

/** Zapewnia szkło, haptykę, dymki i rozwijane listy całemu drzewu interfejsu. */
@Composable
fun GlassScope(haptics: Boolean, content: @Composable BoxScope.() -> Unit) {
    val hints = remember { HintState() }
    val picker = remember { PickerState() }
    CompositionLocalProvider(
        LocalHaptics provides haptics,
        LocalHints provides hints,
        LocalPicker provides picker,
        // Biały tekst domyślnie – poza arkuszami Material tekst byłby czarny.
        androidx.compose.material3.LocalContentColor provides Color.White,
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
            PickerLayer(picker)
            HintLayer(hints)
        }
    }
}

@Composable
private fun PickerLayer(picker: PickerState) {
    var root by remember { mutableStateOf(Rect.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { root = it.boundsInRoot() },
    ) {
        if (picker.visible && picker.tapMode) {
            // Dotknięcie poza listą ją zamyka.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { picker.close() } },
            )
        }
        AnimatedVisibility(
            visible = picker.visible,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(tween(140)),
            modifier = Modifier.offset {
                val a = picker.anchor
                val gap = PICKER_GAP.roundToPx()
                val item = PICKER_ITEM.roundToPx()
                val total = picker.options.size * PICKER_STEP.roundToPx() - (PICKER_STEP - PICKER_ITEM).roundToPx()
                when (picker.direction) {
                    PickerDirection.RIGHT -> IntOffset((a.right - root.left).roundToInt() + gap, (a.center.y - root.top).roundToInt() - item / 2)
                    PickerDirection.LEFT -> IntOffset((a.left - root.left).roundToInt() - gap - total, (a.center.y - root.top).roundToInt() - item / 2)
                    PickerDirection.DOWN -> IntOffset((a.center.x - root.left).roundToInt() - item / 2, (a.bottom - root.top).roundToInt() + gap)
                    PickerDirection.UP -> IntOffset((a.center.x - root.left).roundToInt() - item / 2, (a.top - root.top).roundToInt() - gap - total)
                }
            },
        ) {
            // Pierwsza opcja zawsze najbliżej przycisku – w górę i w lewo lista jest odwrócona.
            val reversed = picker.direction == PickerDirection.UP || picker.direction == PickerDirection.LEFT
            val items: @Composable () -> Unit = {
                val indexed = picker.options.withIndex().toList().let { if (reversed) it.reversed() else it }
                indexed.forEach { (i, o) ->
                    Box(if (picker.tapMode) Modifier.pointerInput(i) { detectTapGestures { picker.pickTap(i) } } else Modifier) {
                        PickerItem(o, i == picker.highlighted, i == picker.selected, picker.rotation)
                    }
                }
            }
            if (picker.horizontal) {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(PICKER_STEP - PICKER_ITEM)) { items() }
            } else {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(PICKER_STEP - PICKER_ITEM),
                ) { items() }
            }
        }
    }
}

/**
 * Opcja rozwijanej listy – bez tła, jak reszta interfejsu aparatu: ikona lub wartość
 * z podpisem i cieniem. Pod palcem – powiększona i pełna biel, bieżąca – kropka.
 */
@Composable
private fun PickerItem(option: PickerOption, highlighted: Boolean, current: Boolean, rotation: Float) {
    val alpha by animateFloatAsState(if (highlighted || current) 1f else 0.7f, tween(90), label = "pickAlpha")
    val scale by animateFloatAsState(if (highlighted) 1.25f else 1f, spring(dampingRatio = 0.5f, stiffness = 800f), label = "pickScale")
    val shadow = androidx.compose.ui.text.TextStyle(
        shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 1f), 6f),
    )
    Box(
        Modifier
            .size(PICKER_ITEM)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = if (highlighted) 0.72f else 0.46f))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .semantics { contentDescription = option.label },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.graphicsLayer { rotationZ = rotation },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (option.icon != null) {
                Icon(option.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            } else {
                Text(option.value ?: option.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, style = shadow)
            }
            Text(
                option.caption ?: option.label,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = shadow,
            )
            if (current) {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

@Composable
private fun HintLayer(hints: HintState) {
    val density = LocalDensity.current
    var bubble by remember { mutableStateOf(IntSize.Zero) }
    var rootOffset by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(hints.token) {
        if (hints.text != null) {
            delay(1600)
            hints.hide()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOffset = it.boundsInRoot() },
    ) {
        val text = hints.text
        AnimatedVisibility(
            visible = text != null,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.92f),
            modifier = Modifier.offset {
                val a = hints.anchor
                val gap = with(density) { 10.dp.roundToPx() }
                val x = (a.center.x - rootOffset.left - bubble.width / 2f).roundToInt()
                val above = (a.top - rootOffset.top - bubble.height - gap).roundToInt()
                val y = if (above > gap) above else (a.bottom - rootOffset.top + gap).roundToInt()
                val maxX = (rootOffset.width - bubble.width - gap).roundToInt().coerceAtLeast(gap)
                IntOffset(x.coerceIn(gap, maxX), y)
            },
        ) {
            Text(
                text ?: "",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .onSizeChanged { bubble = it }
                    .glass(RoundedCornerShape(50), GlassLevel.Regular)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/**
 * Reaktywna powierzchnia dotyku: sprężyste wciśnięcie, haptyka, po przytrzymaniu dymek
 * z nazwą ([label]; [onLongPress] go zastępuje). Z [options]: przytrzymanie i przeciągnięcie
 * w dół (w pionowym pasku – w bok) rozwija listę – wybór jednym ruchem, puszczeniem palca.
 */
@Composable
fun Modifier.reactive(
    label: String?,
    enabled: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    options: List<PickerOption>? = null,
    selected: Int = -1,
    onPick: (Int) -> Unit = {},
    pickerRotation: Float = 0f,
    onClick: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val haptics = LocalHaptics.current
    val hints = LocalHints.current
    val picker = LocalPicker.current
    val hintOnTap = LocalHintOnTap.current
    val pickerDirection = LocalPickerDirection.current
    val tapOpens = LocalTapOpensPicker.current
    val density = LocalDensity.current
    var pressed by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongPress)
    val currentLabel by rememberUpdatedState(label)
    val currentOptions by rememberUpdatedState(options)
    val currentSelected by rememberUpdatedState(selected)
    val pick by rememberUpdatedState(onPick)
    val rotationNow by rememberUpdatedState(pickerRotation)
    // Po dotknięciu / wyborze: dymek z etykietą po zmianie stanu (czyli już nową wartością).
    var tapped by remember { mutableStateOf(0) }
    LaunchedEffect(tapped) {
        if (tapped == 0) return@LaunchedEffect
        withFrameNanos { }
        currentLabel?.let { hints?.show(it, bounds) }
    }
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        spring(dampingRatio = 0.45f, stiffness = 700f),
        label = "press",
    )
    return this
        .onGloballyPositioned { bounds = it.boundsInRoot() }
        // Gest przed skalowaniem – współrzędne palca w układzie nieprzekształconym.
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                pressed = true
                if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val slop = viewConfiguration.touchSlop
                val longTimeout = viewConfiguration.longPressTimeoutMillis
                var expanded = false
                var moved = false
                var longFired = false
                try {
                    while (true) {
                        val waitFor = if (!moved && !longFired && !expanded) longTimeout else Long.MAX_VALUE
                        val event = if (waitFor == Long.MAX_VALUE) {
                            awaitPointerEvent()
                        } else {
                            withTimeoutOrNull(waitFor) { awaitPointerEvent() }
                        }
                        if (event == null) {
                            longFired = true
                            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val l = long
                            val text = currentLabel
                            if (tapOpens && currentOptions != null) {
                                // Przytrzymanie = szybka akcja przycisku.
                                click()
                                if (text != null) tapped++
                            } else if (l != null) {
                                l()
                            } else if (text != null) {
                                hints?.show(text, bounds)
                            }
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (expanded) {
                                val i = picker?.highlighted ?: -1
                                picker?.close()
                                if (i >= 0) {
                                    pick(i)
                                    if (hintOnTap) tapped++
                                }
                            } else if (!moved && !longFired) {
                                val opts = currentOptions
                                if (tapOpens && opts != null && picker != null) {
                                    picker.openTap(opts, currentSelected, bounds, pickerDirection) { i ->
                                        pick(i)
                                        if (hintOnTap) tapped++
                                    }
                                } else {
                                    click()
                                    if (hintOnTap) tapped++
                                }
                            }
                            change.consume()
                            break
                        }
                        val delta = change.position - down.position
                        val along = when (pickerDirection) {
                            PickerDirection.DOWN -> delta.y
                            PickerDirection.UP -> -delta.y
                            PickerDirection.RIGHT -> delta.x
                            PickerDirection.LEFT -> -delta.x
                        }
                        val opts = currentOptions
                        if (!expanded && opts != null && picker != null && along > slop) {
                            expanded = true
                            moved = true
                            hints?.hide()
                            picker.open(opts, currentSelected, bounds, pickerDirection, rotationNow)
                            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (!expanded && delta.getDistance() > slop) {
                            moved = true
                        }
                        if (expanded && picker != null) {
                            change.consume()
                            if (picker.track(bounds.topLeft + change.position, density) && picker.highlighted >= 0 && haptics) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                } finally {
                    pressed = false
                    if (expanded) picker?.close()
                }
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (enabled) 1f else 0.38f
        }
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
}

/** Okrągły szklany przycisk z ikoną; zmiana ikony – płynne przenikanie z obrotem. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    label: String,
    rotation: Float,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    active: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    badge: String? = null,
    level: GlassLevel = GlassLevel.Thin,
    onLongPress: (() -> Unit)? = null,
    options: List<PickerOption>? = null,
    selected: Int = -1,
    onPick: (Int) -> Unit = {},
    onClick: () -> Unit,
) {
    val iconTint by animateColorAsState(tint ?: if (active) activeContent() else Color.White, tween(200), label = "iconTint")
    Box(
        modifier
            .reactive(label, enabled, onLongPress, options, selected, onPick, rotation, onClick)
            .size(size)
            .buttonSurface(BlockShape, level, active),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { rotationZ = rotation }, contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = icon,
                transitionSpec = { (fadeIn(tween(160)) + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.6f)) },
                label = "icon",
            ) { ic ->
                Icon(ic, null, tint = iconTint, modifier = Modifier.size(size * 0.5f))
            }
            if (active && LocalNoBackground.current) ActiveDot(size)
            if (badge != null) {
                Text(
                    badge,
                    color = Palette.Ink,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 9.dp, y = 7.dp)
                        .background(Palette.Amber, RoundedCornerShape(50))
                        .padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** Kropka pod ikoną – znacznik aktywności, gdy przyciski nie mają tła. */
@Composable
private fun BoxScope.ActiveDot(size: Dp) {
    Box(
        Modifier
            .align(Alignment.Center)
            .offset(y = size * 0.36f)
            .size(4.dp)
            .clip(CircleShape)
            .background(Color.White),
    )
}

/** Tło przycisku: własne szkło, a w pasku narzędzi – płaskie, z bursztynem dla aktywnych. */
@Composable
private fun Modifier.buttonSurface(shape: Shape, level: GlassLevel, active: Boolean): Modifier {
    if (LocalNoBackground.current) return this.clip(shape)
    if (!LocalToolbar.current) return glass(shape, level, active = active)
    val bg by animateColorAsState(if (active) ActiveTint else Color.Transparent, tween(200), label = "flatBg")
    return this.clip(shape).background(bg)
}

/**
 * Okrągły przycisk z wartością (np. „4K” nad „2160”, „24” nad „kl/s”). Mieści się w kole,
 * więc po obróceniu telefonu nic się nie obcina.
 */
@Composable
fun GlassValueButton(
    value: String,
    unit: String?,
    label: String,
    rotation: Float,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    active: Boolean = false,
    enabled: Boolean = true,
    options: List<PickerOption>? = null,
    selected: Int = -1,
    onPick: (Int) -> Unit = {},
    onClick: () -> Unit,
) {
    val color by animateColorAsState(if (active) activeContent() else Color.White, tween(200), label = "valueColor")
    Box(
        modifier
            .reactive(label, enabled, null, options, selected, onPick, rotation, onClick)
            .size(size)
            .buttonSurface(BlockShape, GlassLevel.Thin, active),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier.graphicsLayer { rotationZ = rotation },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = value,
                transitionSpec = { (fadeIn(tween(150)) + scaleIn(initialScale = 0.7f)) togetherWith (fadeOut(tween(100)) + scaleOut(targetScale = 0.7f)) },
                label = "value",
            ) { v ->
                Text(
                    v,
                    color = color,
                    fontSize = if (v.length > 3) 11.sp else 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(lineHeight = 14.sp),
                )
            }
            if (unit != null) {
                Text(
                    unit,
                    color = color.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(lineHeight = 9.sp),
                )
            }
        }
    }
}

/**
 * Pasek przytwierdzony do krawędzi: poziomo – czarny pas na całą szerokość u góry, pionowo –
 * czarna kolumna przy lewej krawędzi z lekko zaokrąglonymi rogami.
 */
@Composable
fun DockBar(
    vertical: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalToolbar provides true) {
        if (vertical) {
            androidx.compose.foundation.layout.Column(
                modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black)
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
        } else {
            Row(
                modifier
                    .background(Color.Black)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = SpreadOrPack,
                verticalAlignment = Alignment.CenterVertically,
            ) { content() }
        }
    }
}

/** Szklany pasek narzędzi: jedna kapsuła z płaskimi przyciskami w środku. */
@Composable
fun GlassToolbar(
    vertical: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalToolbar provides true) {
        if (vertical) {
            androidx.compose.foundation.layout.Column(
                modifier
                    .glass(RoundedCornerShape(50), GlassLevel.Regular)
                    .padding(4.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
        } else {
            Row(
                modifier
                    .glass(RoundedCornerShape(50), GlassLevel.Regular)
                    .padding(4.dp),
                horizontalArrangement = SpreadOrPack,
                verticalAlignment = Alignment.CenterVertically,
            ) { content() }
        }
    }
}

/** Szklana pigułka z krótkim tekstem (np. „4K”, „60”), opcjonalnie z ikoną. */
@Composable
fun GlassChip(
    text: String,
    label: String,
    rotation: Float,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color by animateColorAsState(if (active) activeContent() else Color.White, tween(200), label = "chipText")
    Box(
        modifier
            .reactive(label, enabled, onClick = onClick)
            .heightIn(min = 44.dp)
            .glass(BlockShape, active = active)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.rotatedLayout(rotation), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
            }
            AnimatedContent(
                targetState = text,
                transitionSpec = { (fadeIn(tween(150)) + scaleIn(initialScale = 0.8f)) togetherWith fadeOut(tween(100)) },
                label = "chip",
            ) { t ->
                Text(t, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
