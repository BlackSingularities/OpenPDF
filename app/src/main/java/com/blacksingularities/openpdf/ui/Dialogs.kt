package com.blacksingularities.openpdf.ui

import com.blacksingularities.openpdf.i18n.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openpdf.pdf.FieldEdit
import com.blacksingularities.openpdf.pdf.FieldInfo
import com.blacksingularities.openpdf.pdf.FieldKind
import com.blacksingularities.openpdf.pdf.PointN
import com.blacksingularities.openpdf.pdf.simplify
import com.blacksingularities.openpdf.ui.components.DarkDialog
import com.blacksingularities.openpdf.ui.components.DarkField
import com.blacksingularities.openpdf.ui.components.PillButton
import com.blacksingularities.openpdf.ui.theme.Palette
import kotlin.math.max
import kotlin.math.roundToInt

/** Wszystkie okna dialogowe zależne od stanu modelu. */
@Composable
fun ViewModelDialogs(vm: PdfViewModel) {
    vm.passwordRequest?.let { req ->
        PasswordDialog(req.name, req.wrong, onDismiss = { vm.passwordRequest = null }, onSubmit = vm::submitPassword)
    }
    vm.discardRequest?.let { proceed ->
        DarkDialog(
            title = tr("Niezapisane zmiany"),
            onDismiss = { vm.discardRequest = null },
            buttons = {
                PillButton(tr("Anuluj"), primary = false) { vm.discardRequest = null }
                PillButton(tr("Odrzuć"), danger = true) {
                    vm.discardRequest = null
                    proceed()
                }
                PillButton(tr("Zapisz")) {
                    vm.discardRequest = null
                    vm.save()
                }
            },
        ) {
            Text(tr("Dokument ma zmiany, które nie zostały zapisane. Co zrobić?"), color = Palette.TextDim, fontSize = 14.sp)
        }
    }
    vm.textRequest?.let { req ->
        TextInputDialog(vm, req)
    }
    vm.noteView?.let { n ->
        DarkDialog(n.title, onDismiss = { vm.noteView = null }, buttons = { PillButton(tr("Zamknij")) { vm.noteView = null } }) {
            Text(n.text, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
    vm.fieldDialog?.let { d ->
        FieldEditDialog(d.field, onDismiss = { vm.fieldDialog = null }) { value -> vm.fillFields(listOf(FieldEdit(d.field.name, value))) }
    }
    if (vm.signaturePad) {
        SignaturePadDialog(onDismiss = { vm.signaturePad = false }, onSave = vm::saveSignature)
    }
}

@Composable
fun PasswordDialog(name: String, wrong: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var pass by remember { mutableStateOf("") }
    DarkDialog(
        title = tr("Plik chroniony hasłem"),
        onDismiss = onDismiss,
        buttons = {
            PillButton(tr("Anuluj"), primary = false, onClick = onDismiss)
            PillButton(tr("Otwórz"), enabled = pass.isNotEmpty()) { onSubmit(pass) }
        },
    ) {
        Text(name, color = Palette.TextDim, fontSize = 13.sp)
        if (wrong) Text(tr("Nieprawidłowe hasło – spróbuj ponownie."), color = Palette.Record, fontSize = 13.sp)
        DarkField(pass, { pass = it }, tr("Hasło"), password = true)
    }
}

@Composable
fun GoToPageDialog(count: Int, current: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("${current + 1}") }
    val n = text.toIntOrNull()
    DarkDialog(
        title = tr("Przejdź do strony"),
        onDismiss = onDismiss,
        buttons = {
            PillButton(tr("Anuluj"), primary = false, onClick = onDismiss)
            PillButton(tr("Przejdź"), enabled = n != null && n in 1..count) { onGo(n!! - 1) }
        },
    ) {
        DarkField(text, { text = it.filter(Char::isDigit).take(6) }, tr("Numer strony (1–{0})", count), number = true)
    }
}

@Composable
private fun TextInputDialog(vm: PdfViewModel, req: TextRequest) {
    var text by remember(req) { mutableStateOf(req.initial) }
    val existing = vm.marks.firstOrNull { it.id == req.editId } as? com.blacksingularities.openpdf.pdf.TextMark
    var size by remember(req) { mutableFloatStateOf(existing?.sizePt ?: vm.style(Tool.TEXT).width) }
    val isNote = req.tool == Tool.NOTE
    DarkDialog(
        title = when {
            isNote && req.editId != null -> tr("Edytuj notatkę")
            isNote -> tr("Nowa notatka")
            req.editId != null -> tr("Edytuj tekst")
            else -> tr("Nowy tekst")
        },
        onDismiss = { vm.textRequest = null },
        buttons = {
            if (req.editId != null) PillButton(tr("Usuń"), danger = true) { vm.submitText("") }
            PillButton(tr("Anuluj"), primary = false) { vm.textRequest = null }
            PillButton(tr("Gotowe"), enabled = text.isNotBlank()) { vm.submitText(text, if (isNote) null else size) }
        },
    ) {
        DarkField(text, { text = it }, if (isNote) tr("Treść notatki") else tr("Tekst"), singleLine = false, minLines = 3)
        if (!isNote) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Rozmiar"), color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                Slider(
                    value = size,
                    onValueChange = { size = it },
                    valueRange = 6f..48f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
                )
                Text("${size.roundToInt()} pt", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
fun FieldEditDialog(field: FieldInfo, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(field) { mutableStateOf(field.value) }
    DarkDialog(
        title = field.label.ifBlank { field.name },
        onDismiss = onDismiss,
        buttons = {
            PillButton(tr("Anuluj"), primary = false, onClick = onDismiss)
            PillButton(tr("Zapisz")) { onSave(value) }
        },
    ) {
        if (field.label.isNotBlank() && field.label != field.name) Text(field.name, color = Palette.TextFaint, fontSize = 11.sp)
        when (field.kind) {
            FieldKind.TEXT -> DarkField(value, { value = it }, tr("Wartość"), singleLine = !field.multiline, minLines = 3)
            FieldKind.CHECKBOX -> {
                val checked = value == "true"
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { value = if (checked) "false" else "true" }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = Color.White)
                    Text(if (checked) tr("Zaznaczone") else tr("Niezaznaczone"), color = Color.White, modifier = Modifier.padding(start = 10.dp))
                }
            }
            FieldKind.RADIO, FieldKind.CHOICE -> {
                field.options.forEachIndexed { i, label ->
                    val export = field.exportOptions.getOrNull(i) ?: label
                    val sel = value == export || value == label
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { value = export }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (sel) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked, null, tint = Color.White)
                        Text(label, color = Color.White, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                if (field.options.isEmpty()) DarkField(value, { value = it }, tr("Wartość"))
            }
            else -> Text(tr("Tego pola nie można edytować w aplikacji."), color = Palette.TextDim)
        }
    }
}

/** Pole do złożenia podpisu palcem lub rysikiem. */
@Composable
fun SignaturePadDialog(onDismiss: () -> Unit, onSave: (List<List<PointN>>, Float) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    var padW by remember { mutableFloatStateOf(1f) }
    var padH by remember { mutableFloatStateOf(1f) }
    DarkDialog(
        title = tr("Podpis"),
        onDismiss = onDismiss,
        buttons = {
            PillButton(tr("Wyczyść"), primary = false) { strokes.clear() }
            PillButton(tr("Anuluj"), primary = false, onClick = onDismiss)
            PillButton(tr("Zapisz"), enabled = strokes.isNotEmpty()) {
                val all = strokes.flatten()
                val minX = all.minOf { it.x }
                val maxX = all.maxOf { it.x }
                val minY = all.minOf { it.y }
                val maxY = all.maxOf { it.y }
                val w = max(1f, maxX - minX)
                val h = max(1f, maxY - minY)
                val norm = strokes.map { s ->
                    simplify(s.map { PointN((it.x - minX) / w, (it.y - minY) / h) }, 0.002f)
                }
                onSave(norm, w / h)
            }
        },
    ) {
        Text(tr("Złóż podpis w ramce. Zostanie zapisany do ponownego użycia."), color = Palette.TextDim, fontSize = 13.sp)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2.6f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { p ->
                                padW = size.width.toFloat()
                                padH = size.height.toFloat()
                                current.clear()
                                current += p
                            },
                            onDragEnd = {
                                if (current.isNotEmpty()) strokes += current.toList()
                                current.clear()
                            },
                            onDragCancel = { current.clear() },
                        ) { change, _ ->
                            change.consume()
                            current += change.position
                        }
                    },
            ) {
                drawLine(Color(0xFFBDBDBD), Offset(size.width * 0.06f, size.height * 0.78f), Offset(size.width * 0.94f, size.height * 0.78f), 1.5f)
                val all = strokes + listOf(current.toList())
                for (s in all) {
                    if (s.isEmpty()) continue
                    val path = Path()
                    path.moveTo(s[0].x, s[0].y)
                    for (i in 1 until s.size) path.lineTo(s[i].x, s[i].y)
                    drawPath(path, Color(0xFF0D2A6B), style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Text(
                "✕",
                color = Color(0xFF9E9E9E),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 10.dp),
            )
        }
    }
}

/** Nowy pusty dokument. */
@Composable
fun BlankDocDialog(onDismiss: () -> Unit, onCreate: (Int, Boolean) -> Unit) {
    var pages by remember { mutableStateOf("1") }
    var landscape by remember { mutableStateOf(false) }
    DarkDialog(
        title = tr("Nowy dokument"),
        onDismiss = onDismiss,
        buttons = {
            PillButton(tr("Anuluj"), primary = false, onClick = onDismiss)
            PillButton(tr("Utwórz"), enabled = (pages.toIntOrNull() ?: 0) in 1..500) { onCreate(pages.toInt(), landscape) }
        },
    ) {
        DarkField(pages, { pages = it.filter(Char::isDigit).take(3) }, tr("Liczba stron A4"), number = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to tr("Pionowo"), true to tr("Poziomo")).forEach { (v, label) ->
                Text(
                    label,
                    color = if (landscape == v) Palette.Ink else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (landscape == v) Color.White else Color.White.copy(alpha = 0.08f))
                        .clickable { landscape = v }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
