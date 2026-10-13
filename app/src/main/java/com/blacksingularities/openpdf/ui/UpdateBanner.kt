package com.blacksingularities.openpdf.ui

import com.blacksingularities.openpdf.i18n.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openpdf.ui.theme.GlassLevel
import com.blacksingularities.openpdf.ui.theme.Palette
import com.blacksingularities.openpdf.ui.theme.glass
import com.blacksingularities.openpdf.update.UpdateState
import kotlin.math.roundToInt

/** Czy jest coś do pokazania o aktualizacji (nowa wersja, pobieranie, gotowa do instalacji). */
val PdfViewModel.updatePending: Boolean
    get() = update is UpdateState.Available || update is UpdateState.Downloading || update is UpdateState.Ready || update is UpdateState.Installing

/**
 * Baner nowej wersji: jednym dotknięciem aplikacja pobiera APK z GitHub Releases i instaluje go
 * (bez przeglądarki). Od Androida 12 kolejne aktualizacje mogą przejść bez potwierdzenia systemu.
 */
@Composable
fun UpdateBanner(vm: PdfViewModel, modifier: Modifier = Modifier) {
    val u = vm.update
    AnimatedVisibility(visible = vm.updatePending && !vm.updateBannerHidden, modifier = modifier) {
        val (title, action) = when (u) {
            is UpdateState.Available -> tr("Nowa wersja {0}", u.info.version) to tr("Aktualizuj")
            is UpdateState.Downloading -> tr("Pobieranie {0} · {1}%", u.info.version, (u.progress * 100).roundToInt()) to null
            is UpdateState.Ready -> tr("Wersja {0} pobrana", u.info.version) to tr("Zainstaluj")
            is UpdateState.Installing -> tr("Instalowanie {0}…", u.info.version) to null
            else -> "" to null
        }
        val notes = (u as? UpdateState.Available)?.info?.notes ?: (u as? UpdateState.Ready)?.info?.notes
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(20.dp), GlassLevel.Thin)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (u is UpdateState.Available) tr("Pobierze się i zainstaluje automatycznie") else tr("Nie zamykaj aplikacji"),
                        color = Palette.TextDim,
                        fontSize = 11.sp,
                    )
                }
                if (action != null) {
                    Text(
                        action,
                        color = Palette.Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .clickable { vm.updateNow() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                if (u is UpdateState.Available) {
                    Icon(
                        Icons.Rounded.Close,
                        tr("Później"),
                        tint = Palette.TextDim,
                        modifier = Modifier.padding(start = 6.dp).size(32.dp).clip(RoundedCornerShape(50)).clickable { vm.updateBannerHidden = true }.padding(6.dp),
                    )
                }
            }
            if (u is UpdateState.Downloading) {
                LinearProgressIndicator(
                    progress = { u.progress },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
            if (!notes.isNullOrBlank()) {
                Text(notes, color = Palette.TextDim, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 34.dp))
            }
        }
    }
}
