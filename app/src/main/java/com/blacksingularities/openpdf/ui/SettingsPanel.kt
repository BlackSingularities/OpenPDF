@file:Suppress("DEPRECATION")

package com.blacksingularities.openpdf.ui

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
import com.blacksingularities.openpdf.ui.components.SwitchRow
import com.blacksingularities.openpdf.ui.components.TabChip
import com.blacksingularities.openpdf.ui.theme.Palette
import com.blacksingularities.openpdf.update.UpdateManager
import com.blacksingularities.openpdf.update.UpdateState
import kotlin.math.roundToInt

@Composable
fun SettingsPanel(vm: PdfViewModel) {
    var tab by remember { mutableIntStateOf(if (vm.updatePending) 3 else 0) }
    val s = vm.settings
    fun set(t: (com.blacksingularities.openpdf.data.AppSettings) -> com.blacksingularities.openpdf.data.AppSettings) = vm.updateSettings(t)
    Panel(tr("Ustawienia"), onClose = { vm.sheet = null }) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TabChip(Icons.AutoMirrored.Rounded.MenuBook, tr("Książka"), tab == 0) { tab = 0 }
            TabChip(Icons.Rounded.LightMode, tr("Wyświetlanie"), tab == 1) { tab = 1 }
            TabChip(Icons.Rounded.Edit, tr("Edycja"), tab == 2) { tab = 2 }
            TabChip(Icons.Rounded.Settings, tr("Ogólne"), tab == 3) { tab = 3 }
        }
        when (tab) {
            0 -> {
                Group(tr("Tryb książki")) {
                    CompactChoice(
                        Icons.AutoMirrored.Rounded.MenuBook,
                        tr("Dwie strony obok siebie"),
                        BookMode.entries,
                        s.bookMode,
                        { it.label },
                        hint = tr("Auto: w poziomie i na rozłożonym foldzie"),
                    ) { v -> set { it.copy(bookMode = v) } }
                    SwitchRow(
                        Icons.Rounded.AutoStories,
                        tr("Okładka osobno"),
                        if (s.coverAlone) tr("Pierwsza strona sama, dalej 2–3, 4–5… (jak w książce)") else tr("Rozkładówki od pierwszej strony: 1–2, 3–4…"),
                        s.coverAlone,
                    ) { v -> set { it.copy(coverAlone = v) } }
                    SwitchRow(
                        Icons.Rounded.FormatTextdirectionRToL,
                        tr("Od prawej do lewej"),
                        tr("Kolejność stron w rozkładówce jak w mandze i językach RTL"),
                        s.rtl,
                    ) { v -> set { it.copy(rtl = v) } }
                    SwitchRow(
                        Icons.Rounded.AutoStories,
                        tr("Przewracanie kartek"),
                        tr("Animacja jak w książce przy przewracaniu stron w poziomie"),
                        s.pageFlip,
                    ) { v -> set { it.copy(pageFlip = v) } }
                    SwitchRow(Icons.Rounded.Gradient, tr("Cień grzbietu"), tr("Delikatne przyciemnienie przy zgięciu stron"), s.spineShadow) { v -> set { it.copy(spineShadow = v) } }
                    CompactChoice(
                        Icons.Rounded.FitScreen,
                        tr("Dopasowanie rozkładówki"),
                        FitMode.entries,
                        s.fitBook,
                        { if (it == FitMode.PAGE) tr("Cała rozkładówka") else tr("Do szerokości") },
                        short = { if (it == FitMode.PAGE) tr("Całość") else tr("Szerokość") },
                    ) { v -> set { it.copy(fitBook = v) } }
                }
                Group(tr("Pojedyncze strony")) {
                    CompactChoice(
                        Icons.Rounded.FitScreen,
                        tr("Dopasowanie strony"),
                        FitMode.entries,
                        s.fitSingle,
                        { if (it == FitMode.PAGE) tr("Cała strona") else tr("Do szerokości") },
                        short = { if (it == FitMode.PAGE) tr("Całość") else tr("Szerokość") },
                    ) { v -> set { it.copy(fitSingle = v) } }
                }
            }
            1 -> {
                Group(tr("Strony")) {
                    CompactChoice(
                        Icons.Rounded.SwapVert,
                        tr("Przewijanie"),
                        ScrollMode.entries,
                        s.scroll,
                        { if (it == ScrollMode.VERTICAL) tr("Ciągłe w pionie") else tr("Przewracanie stron w poziomie") },
                        short = { if (it == ScrollMode.VERTICAL) tr("Ciągłe") else tr("Strony") },
                    ) { v -> set { it.copy(scroll = v) } }
                    CompactChoice(
                        Icons.Rounded.Contrast,
                        tr("Papier"),
                        PaperTheme.entries,
                        s.paper,
                        { it.label },
                        hint = tr("Nocny odwraca jasność stron"),
                    ) { v -> set { it.copy(paper = v) } }
                    SwitchRow(Icons.Rounded.ViewAgenda, tr("Odstęp między stronami"), null, s.pageGap) { v -> set { it.copy(pageGap = v) } }
                }
                Group(tr("Sterowanie")) {
                    SwitchRow(Icons.Rounded.VolumeUp, tr("Klawisze głośności przewracają strony"), null, s.volumeKeys) { v -> set { it.copy(volumeKeys = v) } }
                    SwitchRow(Icons.Rounded.TouchApp, tr("Strefy dotyku przy krawędziach"), tr("W trybie przewracania: dotknięcie lewej/prawej krawędzi zmienia stronę"), s.tapZones) { v -> set { it.copy(tapZones = v) } }
                    SwitchRow(Icons.Rounded.LightMode, tr("Nie wygaszaj ekranu"), tr("Podczas czytania"), s.keepScreenOn) { v -> set { it.copy(keepScreenOn = v) } }
                    SwitchRow(Icons.Rounded.Vibration, tr("Wibracje"), null, s.haptics) { v -> set { it.copy(haptics = v) } }
                }
            }
            2 -> {
                Group(tr("Adnotacje")) {
                    Column(Modifier.padding(12.dp)) {
                        DarkField(s.author, { v -> set { it.copy(author = v.take(60)) } }, tr("Autor adnotacji"))
                    }
                    SwitchRow(Icons.Rounded.Gesture, tr("Rysuj tylko rysikiem"), tr("Palec przewija i powiększa, rysik rysuje"), s.stylusOnly) { v -> set { it.copy(stylusOnly = v) } }
                }
                Group(tr("Jak to działa")) {
                    Text(
                        tr("Rysunki, teksty, kształty, zakreślenia i podpisy są zapisywane jako standardowe adnotacje PDF – ") +
                            tr("widoczne i edytowalne w Acrobacie, przeglądarkach i innych czytnikach. Do chwili zapisu możesz je ") +
                            tr("przesuwać, zmieniać i cofać. Zmiany stron (obrót, kolejność, usuwanie) też da się cofnąć."),
                        color = Palette.TextDim,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            else -> {
                Group(tr("Język")) {
                    CompactChoice(
                        Icons.Rounded.Language,
                        tr("Język aplikacji"),
                        AppLanguage.entries,
                        s.language,
                        { when (it) { AppLanguage.SYSTEM -> tr("Systemowy"); AppLanguage.PL -> "Polski"; AppLanguage.EN -> "English" } },
                        short = { when (it) { AppLanguage.SYSTEM -> "Auto"; AppLanguage.PL -> "PL"; AppLanguage.EN -> "EN" } },
                    ) { v -> set { it.copy(language = v) } }
                }
                Group(tr("Aktualizacje")) {
                    UpdateRow(vm)
                    SwitchRow(Icons.Rounded.SystemUpdate, tr("Sprawdzaj przy uruchomieniu"), null, s.autoUpdate) { v -> set { it.copy(autoUpdate = v) } }
                }
                Group(tr("Ostatnie pliki")) {
                    ActionRow(Icons.Rounded.Person, tr("Wyczyść listę ostatnich plików"), tr("{0} pozycji", vm.recents.size), danger = true) { vm.clearRecents() }
                }
                Group(tr("O aplikacji")) {
                    About()
                }
            }
        }
    }
}

@Composable
private fun UpdateRow(vm: PdfViewModel) {
    val update = vm.update
    val (status, action, handler) = when (update) {
        UpdateState.Idle -> Triple(tr("Wersja {0}", UpdateManager.currentVersion), tr("Sprawdź"), { vm.checkUpdate() })
        UpdateState.Checking -> Triple(tr("Sprawdzanie…"), null, {})
        UpdateState.UpToDate -> Triple(tr("Wersja {0} jest najnowsza", UpdateManager.currentVersion), tr("Sprawdź"), { vm.checkUpdate() })
        is UpdateState.Available -> Triple(tr("Dostępna wersja {0}", update.info.version), tr("Aktualizuj"), { vm.updateNow() })
        is UpdateState.Downloading -> Triple(tr("Pobieranie {0} · {1}%", update.info.version, (update.progress * 100).roundToInt()), null, {})
        is UpdateState.Ready -> Triple(tr("Wersja {0} gotowa", update.info.version), tr("Zainstaluj"), { vm.installUpdate() })
        is UpdateState.Installing -> Triple(tr("Instalowanie {0}…", update.info.version), null, {})
        is UpdateState.Failed -> Triple(update.message, tr("Ponów"), { vm.checkUpdate() })
    }
    Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(Icons.Rounded.SystemUpdate)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("OpenPDF ${UpdateManager.currentVersion}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
private fun About() {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tr("OpenPDF – otwarty edytor PDF dla Androida"), fontSize = 13.sp, color = Color.White)
        Text(tr("Licencja MIT · PdfBox-Android (Apache 2.0)"), fontSize = 11.sp, color = Palette.TextDim)
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
