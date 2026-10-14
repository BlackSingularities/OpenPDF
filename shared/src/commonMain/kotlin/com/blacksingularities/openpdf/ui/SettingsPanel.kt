@file:Suppress("DEPRECATION")

package com.blacksingularities.openpdf.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import com.blacksingularities.openpdf.data.effectiveBookMode
import com.blacksingularities.openpdf.data.effectiveScroll
import com.blacksingularities.openpdf.ui.theme.reactive
import com.blacksingularities.openpdf.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.FormatTextdirectionRToL
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Language
import com.blacksingularities.openpdf.i18n.AppLanguage
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openpdf.data.BookMode
import com.blacksingularities.openpdf.data.PaperTheme
import com.blacksingularities.openpdf.ui.components.ActionRow
import com.blacksingularities.openpdf.ui.components.CompactChoice
import com.blacksingularities.openpdf.ui.components.DarkField
import com.blacksingularities.openpdf.ui.components.Group
import com.blacksingularities.openpdf.ui.components.Panel
import com.blacksingularities.openpdf.ui.components.RowIcon
import com.blacksingularities.openpdf.ui.components.TabChip
import com.blacksingularities.openpdf.ui.theme.Palette
import com.blacksingularities.openpdf.update.UpdateState
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import com.blacksingularities.openpdf.platform.formatDecimal
import kotlinx.coroutines.launch

@Composable
fun SettingsPanel(vm: PdfViewModel, startOnDocument: Boolean = false) {
    val hasDoc = vm.doc != null
    // Dwie zakładki: Dokument i Ustawienia (bez otwartego dokumentu – same ustawienia).
    var docTab by remember { mutableStateOf(startOnDocument && hasDoc) }
    val drag = vm.cropDrag
    // W trakcie przeciągania suwaka przycinania panel jest niewidoczny (ale dalej prowadzi gest),
    // żeby było widać strony; nad nimi tylko dymek z nazwą marginesu i wartością.
    Box(Modifier.fillMaxSize()) {
        Panel(if (hasDoc) tr("Menu") else tr("Ustawienia"), onClose = { vm.sheet = null }, hidden = drag != null) {
            if (hasDoc) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TabChip(Icons.Rounded.Description, tr("Dokument"), docTab) { docTab = true }
                    TabChip(Icons.Rounded.Settings, tr("Ustawienia"), !docTab) { docTab = false }
                }
            }
            if (hasDoc && docTab) {
                DocumentMenu(vm)
            } else {
                SettingsContent(vm)
            }
        }
        if (drag != null) CropDragHud(drag.first, drag.second, Modifier.align(Alignment.TopCenter))
    }
}

/** Wszystkie ustawienia na jednej liście: Książka, Wyświetlanie, Edycja, Ogólne. */
@Composable
private fun ColumnScope.SettingsContent(vm: PdfViewModel) {
    val s = vm.settings
    fun set(t: (com.blacksingularities.openpdf.data.AppSettings) -> com.blacksingularities.openpdf.data.AppSettings) = vm.updateSettings(t)
    SectionHeader(Icons.AutoMirrored.Rounded.MenuBook, tr("Książka"))
        TileGrid(
            listOf(
                Tile(Icons.Rounded.AutoStories, tr("Auto-układ"), tr("Telefon w pionie: jedna strona, przewijanie w dół. Poziomo, tablet i rozłożony fold: dwie strony jak w książce, z przewracaniem kartek."), s.autoLayout) { v -> set { it.copy(autoLayout = v) } },
                Tile(Icons.AutoMirrored.Rounded.MenuBook, tr("Okładka osobno"), if (s.coverAlone) tr("Pierwsza strona sama, dalej 2–3, 4–5… (jak w książce)") else tr("Rozkładówki od pierwszej strony: 1–2, 3–4…"), s.coverAlone) { v -> set { it.copy(coverAlone = v) } },
                Tile(Icons.Rounded.FormatTextdirectionRToL, tr("Od prawej"), tr("Kolejność stron w rozkładówce jak w mandze i językach RTL"), s.rtl) { v -> set { it.copy(rtl = v) } },
                Tile(Icons.Rounded.Flip, tr("Kartki"), tr("Animacja jak w książce przy przewracaniu stron w poziomie"), s.pageFlip) { v -> set { it.copy(pageFlip = v) } },
                Tile(Icons.Rounded.Gradient, tr("Cień"), tr("Delikatne przyciemnienie przy zgięciu stron"), s.spineShadow) { v -> set { it.copy(spineShadow = v) } },
            ),
        )
        Group(null) {
            CompactChoice(
                Icons.AutoMirrored.Rounded.MenuBook,
                tr("Dwie strony obok siebie"),
                BookMode.entries,
                s.effectiveBookMode,
                { it.label },
                short = { when (it) { BookMode.AUTO -> "Auto"; BookMode.ALWAYS -> "2"; BookMode.NEVER -> "1" } },
            ) { v -> set { it.copy(bookMode = v, autoLayout = v == BookMode.AUTO && it.autoLayout) } }
            CompactChoice(
                Icons.Rounded.FitScreen,
                tr("Dopasowanie rozkładówki"),
                FitMode.entries,
                s.fitBook,
                { if (it == FitMode.PAGE) tr("Cała rozkładówka") else tr("Do szerokości") },
                short = { if (it == FitMode.PAGE) tr("Całość") else tr("Szerokość") },
            ) { v -> set { it.copy(fitBook = v) } }
            CompactChoice(
                Icons.Rounded.FitScreen,
                tr("Dopasowanie strony"),
                FitMode.entries,
                s.fitSingle,
                { if (it == FitMode.PAGE) tr("Cała strona") else tr("Do szerokości") },
                short = { if (it == FitMode.PAGE) tr("Całość") else tr("Szerokość") },
            ) { v -> set { it.copy(fitSingle = v) } }
        }
    SectionHeader(Icons.Rounded.LightMode, tr("Wyświetlanie"))
        TileGrid(
            listOf(
                Tile(Icons.Rounded.ViewAgenda, tr("Odstęp"), tr("Odstęp między stronami"), s.pageGap) { v -> set { it.copy(pageGap = v) } },
                Tile(Icons.Rounded.FitScreen, tr("Bez marginesów"), tr("Bez marginesów – strona dotyka boków ekranu"), s.edgeToEdge) { v -> set { it.copy(edgeToEdge = v) } },
                Tile(Icons.Rounded.Fullscreen, tr("Bez paska stanu"), tr("Więcej miejsca na czytanie – pasek pojawia się po przesunięciu od góry"), s.hideStatusBar) { v -> set { it.copy(hideStatusBar = v) } },
                Tile(Icons.Rounded.TouchApp, tr("Strefy dotyku"), tr("W trybie przewracania: dotknięcie lewej/prawej krawędzi zmienia stronę"), s.tapZones) { v -> set { it.copy(tapZones = v) } },
            ),
        )
        Group(null) {
            CompactChoice(
                Icons.Rounded.Contrast,
                tr("Papier"),
                PaperTheme.entries,
                s.paper,
                { it.label },
            ) { v -> set { it.copy(paper = v) } }
            CompactChoice(
                Icons.Rounded.SwapVert,
                tr("Przewijanie"),
                ScrollMode.entries,
                s.effectiveScroll(false),
                { if (it == ScrollMode.VERTICAL) tr("Ciągłe w pionie") else tr("Przewracanie stron w poziomie") },
                short = { if (it == ScrollMode.VERTICAL) tr("Ciągłe") else tr("Strony") },
            ) { v -> set { it.copy(scroll = v, autoLayout = false) } }
        }
        CropControl(vm)
    SectionHeader(Icons.Rounded.Edit, tr("Edycja"))
        Group(null) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                DarkField(s.author, { v -> set { it.copy(author = v.take(60)) } }, tr("Autor adnotacji"))
            }
        }
        TileGrid(
            listOf(
                Tile(Icons.Rounded.Gesture, tr("Tylko rysik"), tr("Palec przewija i powiększa, rysik rysuje"), s.stylusOnly) { v -> set { it.copy(stylusOnly = v) } },
            ),
        )
        Text(
            tr("Rysunki, teksty, kształty, zakreślenia i podpisy są zapisywane jako standardowe adnotacje PDF – ") +
                tr("widoczne i edytowalne w Acrobacie, przeglądarkach i innych czytnikach. Do chwili zapisu możesz je ") +
                tr("przesuwać, zmieniać i cofać. Zmiany stron (obrót, kolejność, usuwanie) też da się cofnąć."),
            color = Palette.TextDim,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    SectionHeader(Icons.Rounded.Settings, tr("Ogólne"))
        Group(null) {
            CompactChoice(
                Icons.Rounded.Language,
                tr("Język aplikacji"),
                AppLanguage.entries,
                s.language,
                { when (it) { AppLanguage.SYSTEM -> tr("Systemowy"); AppLanguage.PL -> "Polski"; AppLanguage.EN -> "English" } },
                short = { when (it) { AppLanguage.SYSTEM -> "Auto"; AppLanguage.PL -> "PL"; AppLanguage.EN -> "EN" } },
            ) { v -> set { it.copy(language = v) } }
        }
        TileGrid(
            listOfNotNull(
                Tile(Icons.Rounded.SystemUpdate, tr("Aktualizacje"), tr("Sprawdzaj przy uruchomieniu"), s.autoUpdate) { v -> set { it.copy(autoUpdate = v) } },
                Tile(Icons.Rounded.Vibration, tr("Wibracja przycisków"), tr("Lekka wibracja przy dotknięciu i przytrzymaniu przycisków"), s.haptics) { v -> set { it.copy(haptics = v) } },
                Tile(Icons.Rounded.LightMode, tr("Ekran włączony"), tr("Nie wygaszaj ekranu"), s.keepScreenOn) { v -> set { it.copy(keepScreenOn = v) } },
                if (vm.platform.hasVolumeKeys) {
                    Tile(Icons.Rounded.VolumeUp, tr("Strony głośnością"), tr("Klawisze głośności przewracają strony"), s.volumeKeys) { v -> set { it.copy(volumeKeys = v) } }
                } else {
                    null
                },
            ),
        )
        Group(null) {
            vm.platform.settingsExtras?.invoke()
            UpdateRow(vm)
            ActionRow(Icons.Rounded.Person, tr("Wyczyść listę ostatnich plików"), tr("{0} pozycji", vm.recents.size), danger = true) { vm.clearRecents() }
        }
        About(vm)
    Spacer(Modifier.height(12.dp))
}

/** Nagłówek sekcji ustawień: ikona, nazwa i cienka linia do krawędzi. */
@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
    }
}

/** Kafelek-przełącznik: ikona i krótki podpis; pełny opis po przytrzymaniu. */
private class Tile(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val short: String,
    val full: String,
    val value: Boolean,
    val onChange: (Boolean) -> Unit,
)

@Composable
private fun TileGrid(tiles: List<Tile>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { t ->
                    Column(
                        Modifier
                            .weight(1f)
                            .reactive(t.full) { t.onChange(!t.value) }
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (t.value) Color.White else Color.White.copy(alpha = 0.06f))
                            .padding(vertical = 9.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(t.icon, null, tint = if (t.value) Palette.Ink else Palette.TextDim, modifier = Modifier.size(20.dp))
                        Text(
                            t.short,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = if (t.value) Palette.Ink else Palette.TextDim,
                            fontWeight = if (t.value) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Przycinanie marginesów: osobno dla pojedynczych stron i dla rozkładówki. Rozkładówka jest
 * przycinana jako całość – lewy margines z lewej strony, prawy z prawej, bez ruszania grzbietu.
 * W trakcie przeciągania suwaka panel znika, a strony od razu pokazują przycięcie.
 */
@Composable
private fun CropControl(vm: PdfViewModel) {
    val s = vm.settings
    var spreadTab by remember(vm.bookView) { mutableStateOf(vm.bookView) }
    val labels = listOf(tr("Lewy"), tr("Górny"), tr("Prawy"), tr("Dolny"))
    Group(tr("Przycinanie marginesów")) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TabChip(Icons.Rounded.Description, tr("Strony"), !spreadTab) { spreadTab = false }
            TabChip(Icons.AutoMirrored.Rounded.MenuBook, tr("Rozkładówka"), spreadTab) { spreadTab = true }
        }
        if (!spreadTab) {
            CropEditor(vm, floatArrayOf(s.cropLeft, s.cropTop, s.cropRight, s.cropBottom), spread = false, labels = labels) { (l, t, r, b) ->
                { it.copy(cropLeft = l, cropTop = t, cropRight = r, cropBottom = b) }
            }
        } else {
            Text(
                tr("Lewy margines z lewej strony, prawy z prawej – grzbiet bez zmian"),
                color = Palette.TextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            CropEditor(vm, floatArrayOf(s.spreadCropLeft, s.spreadCropTop, s.spreadCropRight, s.spreadCropBottom), spread = true, labels = labels) { (l, t, r, b) ->
                { it.copy(spreadCropLeft = l, spreadCropTop = t, spreadCropRight = r, spreadCropBottom = b) }
            }
        }
    }
}

/** Krok przycisków −/+ (0,1%); przytrzymanie powtarza krok coraz szybciej. */
private const val CROP_STEP = 0.001f

/** Zakres suwaków w trybie dokładnym – trzy razy drobniejszy ruch niż na pełnym zakresie. */
private const val CROP_FINE_RANGE = 0.1f

private fun snapCrop(v: Float): Float =
    ((v / CROP_STEP).roundToInt() * CROP_STEP).coerceIn(0f, com.blacksingularities.openpdf.data.MAX_CROP)

/**
 * Suwaki czterech marginesów i suwak „Wszystkie” (przesuwa wszystkie marginesy naraz, zachowując
 * różnice między nimi); [spread] – podgląd dwóch stron z grzbietem. [toSettings] buduje zmianę
 * ustawień z wartości [lewy, górny, prawy, dolny].
 */
@Composable
private fun CropEditor(
    vm: PdfViewModel,
    values: FloatArray,
    spread: Boolean,
    labels: List<String>,
    toSettings: (FloatArray) -> ((com.blacksingularities.openpdf.data.AppSettings) -> com.blacksingularities.openpdf.data.AppSettings),
) {
    // Podgląd na żywo bez zapisu; zapis dopiero po puszczeniu suwaka/przycisku.
    fun preview(v: FloatArray) = vm.previewSettings(toSettings(FloatArray(4) { snapCrop(v[it]) }))
    fun commit() = vm.updateSettings { it }
    var fine by remember(spread) { mutableStateOf(values.all { it <= CROP_FINE_RANGE }) }
    val range = if (fine) CROP_FINE_RANGE else com.blacksingularities.openpdf.data.MAX_CROP
    // Na wypadek zamknięcia panelu w trakcie przeciągania – panel nie może zostać niewidoczny.
    DisposableEffect(Unit) { onDispose { vm.cropDrag = null } }

    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CropPreview(values[0], values[1], values[2], values[3], spread)
        Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(tr("Brak") to 0f, "1%" to 0.01f, "2%" to 0.02f, "4%" to 0.04f, "8%" to 0.08f).forEach { (label, p) ->
                    CropChip(label, false) {
                        preview(FloatArray(4) { p })
                        commit()
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Zakres"), fontSize = 11.sp, color = Palette.TextDim)
                CropChip("0–10%", fine) { fine = true }
                CropChip("0–30%", !fine) { fine = false }
            }
        }
    }

    // Wszystkie naraz: suwak stoi na najmniejszym marginesie, pozostałe przesuwają się o tyle samo.
    var base by remember { mutableStateOf<FloatArray?>(null) }
    val allLabel = tr("Wszystkie")
    fun shiftAll(from: FloatArray, to: Float) {
        val d = to - from.min()
        preview(FloatArray(4) { from[it] + d })
    }
    CropSlider(
        label = allLabel,
        value = values.min(),
        range = range,
        strong = true,
        onDrag = { pos ->
            val b = base ?: values.copyOf().also { base = it }
            shiftAll(b, pos)
            vm.cropDrag = allLabel to snapCrop(pos)
        },
        onStep = { d -> shiftAll(values, values.min() + d) },
        onDone = {
            base = null
            vm.cropDrag = null
            commit()
        },
    )
    for (i in 0 until 4) {
        CropSlider(
            label = labels[i],
            value = values[i],
            range = range,
            strong = false,
            onDrag = { pos ->
                preview(values.copyOf().also { it[i] = pos })
                vm.cropDrag = labels[i] to snapCrop(pos)
            },
            onStep = { d -> preview(values.copyOf().also { it[i] += d }) },
            onDone = {
                vm.cropDrag = null
                commit()
            },
        )
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun CropChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Palette.Ink else Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Podgląd: szara strona (albo rozkładówka), biały – to, co zostaje na ekranie. */
@Composable
private fun CropPreview(l: Float, t: Float, r: Float, b: Float, spread: Boolean) {
    val w = if (spread) 76.dp else 44.dp
    Box(Modifier.size(width = w, height = 60.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.18f))) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val top = size.height * t
            val h = size.height * (1f - t - b)
            if (!spread) {
                drawRect(
                    Color.White,
                    androidx.compose.ui.geometry.Offset(size.width * l, top),
                    androidx.compose.ui.geometry.Size(size.width * (1f - l - r), h),
                )
            } else {
                // Każda strona to połowa podglądu; przycinane są tylko krawędzie zewnętrzne.
                val half = size.width / 2f
                drawRect(
                    Color.White,
                    androidx.compose.ui.geometry.Offset(half * l, top),
                    androidx.compose.ui.geometry.Size(half * (1f - l), h),
                )
                drawRect(
                    Color.White,
                    androidx.compose.ui.geometry.Offset(half, top),
                    androidx.compose.ui.geometry.Size(half * (1f - r), h),
                )
                drawLine(
                    Color.Black.copy(alpha = 0.35f),
                    androidx.compose.ui.geometry.Offset(half, top),
                    androidx.compose.ui.geometry.Offset(half, top + h),
                    1.5f,
                )
            }
        }
    }
}

/**
 * Suwak marginesu na całą szerokość panelu, z przyciskami −/+ co 0,1% (przytrzymanie powtarza).
 * [onDrag] – każdy ruch suwaka, [onStep] – krok przycisku, [onDone] – koniec gestu (zapis).
 */
@Composable
private fun CropSlider(
    label: String,
    value: Float,
    range: Float,
    strong: Boolean,
    onDrag: (Float) -> Unit,
    onStep: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (strong) Color.White else Palette.TextDim,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.width(68.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        CropNudge("−", { onStep(-CROP_STEP) }, onDone)
        androidx.compose.material3.Slider(
            value = value.coerceAtMost(range),
            onValueChange = { onDrag(snapCrop(it)) },
            onValueChangeFinished = onDone,
            valueRange = 0f..range,
            modifier = Modifier.weight(1f).height(36.dp).padding(horizontal = 4.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = if (strong) Palette.Amber else Color.White,
                activeTrackColor = if (strong) Palette.Amber else Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
            ),
        )
        CropNudge("+", { onStep(CROP_STEP) }, onDone)
        Text(
            formatDecimal(value * 100f, 1) + "%",
            fontSize = 12.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(48.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** Przycisk kroku: dotknięcie – jeden krok, przytrzymanie – powtarzanie, coraz szybsze. */
@Composable
private fun CropNudge(sign: String, onStep: () -> Unit, onDone: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val done by rememberUpdatedState(onDone)
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val repeat = scope.launch {
                        step()
                        kotlinx.coroutines.delay(400)
                        var pause = 90L
                        while (true) {
                            step()
                            kotlinx.coroutines.delay(pause)
                            pause = (pause - 6).coerceAtLeast(25L)
                        }
                    }
                    waitForUpOrCancellation()
                    repeat.cancel()
                    done()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(sign, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** Dymek nad stronami w trakcie przeciągania suwaka, gdy panel ustawień jest ukryty. */
@Composable
private fun CropDragHud(label: String, value: Float, modifier: Modifier) {
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr("Przycinanie") + " · " + label, color = Palette.TextDim, fontSize = 13.sp)
        Spacer(Modifier.width(10.dp))
        Text(formatDecimal(value * 100f, 1) + "%", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}


@Composable
private fun UpdateRow(vm: PdfViewModel) {
    val update = vm.update
    val (status, action, handler) = when (update) {
        UpdateState.Idle -> Triple(tr("Wersja {0}", vm.platform.appVersion), tr("Sprawdź"), { vm.checkUpdate() })
        UpdateState.Checking -> Triple(tr("Sprawdzanie…"), null, {})
        UpdateState.UpToDate -> Triple(tr("Wersja {0} jest najnowsza", vm.platform.appVersion), tr("Sprawdź"), { vm.checkUpdate() })
        is UpdateState.Available -> Triple(tr("Dostępna wersja {0}", update.info.version), if (vm.platform.canSelfUpdate) tr("Aktualizuj") else tr("Pobierz"), { vm.updateNow() })
        is UpdateState.Downloading -> Triple(tr("Pobieranie {0} · {1}%", update.info.version, (update.progress * 100).roundToInt()), null, {})
        is UpdateState.Ready -> Triple(tr("Wersja {0} gotowa", update.info.version), tr("Zainstaluj"), { vm.installUpdate() })
        is UpdateState.Installing -> Triple(tr("Instalowanie {0}…", update.info.version), null, {})
        is UpdateState.Failed -> Triple(update.message, tr("Ponów"), { vm.checkUpdate() })
    }
    Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(Icons.Rounded.SystemUpdate)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("OpenPDF ${vm.platform.appVersion}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(status, fontSize = 12.sp, color = Palette.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (action != null) {
                Text(
                    action,
                    color = Palette.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Palette.Amber)
                        .clickable(onClick = handler)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
        if (update is UpdateState.Downloading) {
            LinearProgressIndicator(
                progress = { update.progress },
                color = Palette.Amber,
                trackColor = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        val notes = (update as? UpdateState.Available)?.info?.notes ?: (update as? UpdateState.Ready)?.info?.notes
        if (!notes.isNullOrBlank()) {
            Text(notes, fontSize = 12.sp, lineHeight = 17.sp, color = Palette.TextDim, modifier = Modifier.padding(top = 8.dp, start = 44.dp))
        }
    }
}

@Composable
private fun About(vm: PdfViewModel) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tr(vm.platform.aboutTitle), fontSize = 13.sp, color = Color.White)
        Text(tr(vm.platform.aboutLicense), fontSize = 11.sp, color = Palette.TextDim)
        Text(
            "github.com/BlackSingularities/OpenPDF",
            fontSize = 12.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { runCatching { uri.openUri("https://github.com/BlackSingularities/OpenPDF") } }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            tr("Pomysł i wykonanie: redzikowski.com"),
            fontSize = 12.sp,
            color = Palette.TextDim,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { runCatching { uri.openUri("https://redzikowski.com") } }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Suppress("unused")
private val infoIcon = Icons.Rounded.Info
