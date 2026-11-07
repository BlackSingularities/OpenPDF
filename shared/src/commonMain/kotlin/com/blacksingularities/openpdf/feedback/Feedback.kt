package com.blacksingularities.openpdf.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openpdf.i18n.tr
import com.blacksingularities.openpdf.platform.Platform
import com.blacksingularities.openpdf.platform.fileSystem
import com.blacksingularities.openpdf.platform.ioDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Path

/**
 * Zgłoszenia do redzikowski.com – wspólne dla Androida i iOS.
 *  - raport o błędzie (awaria z poprzedniego uruchomienia) → „Raporty błędów” w panelu,
 *  - zgłoszenie błędu / sugestia funkcjonalności → „Sugestie i zgłoszenia”.
 * E-mail jest opcjonalny; nic nie jest wysyłane bez decyzji użytkownika.
 */
object Feedback {
    const val ENDPOINT = "https://redzikowski.com/api/feedback"
    const val APP = "openpdf"
    private const val CRASH_FILE = "feedback-pending-crash.txt"

    fun crashFile(platform: Platform): Path = platform.filesDir / CRASH_FILE

    /** Zapis awarii – wołany z obsługi nieprzechwyconych wyjątków (iOS). */
    fun recordCrash(platform: Platform, stack: String) {
        runCatching {
            fileSystem.write(crashFile(platform)) { writeUtf8("${platform.appVersion}\n${stack.take(60_000)}") }
        }
    }

    fun pendingCrash(platform: Platform): Pair<String, String>? = runCatching {
        val file = crashFile(platform)
        if (!fileSystem.exists(file)) return null
        val text = fileSystem.read(file) { readUtf8() }
        val version = text.substringBefore('\n')
        version to text.substringAfter('\n')
    }.getOrNull()

    fun discardCrash(platform: Platform) {
        runCatching { fileSystem.delete(crashFile(platform)) }
    }

    suspend fun send(
        platform: Platform,
        kind: String,
        message: String,
        email: String,
        diagnostics: Boolean = true,
        stack: String = "",
        version: String = platform.appVersion,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val body = buildJsonObject {
                put("kind", kind)
                put("app", APP)
                put("appName", "OpenDocument")
                put("appVersion", version)
                put("platform", platform.feedbackPlatform)
                put("locale", platform.systemLanguage())
                put("message", message.trim())
                put("email", email.trim())
                if (stack.isNotBlank()) put("stack", stack)
                if (diagnostics || kind == "crash") {
                    put("os", platform.osDescription)
                    put("device", platform.deviceDescription)
                }
            }
            val code = platform.postJson(ENDPOINT, body.toString())
            if (code !in 200..299) error("HTTP $code")
        }
    }
}

/** Formularz zgłoszenia błędu albo sugestii. */
@Composable
fun FeedbackDialog(platform: Platform, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var feature by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var diagnostics by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(tr("Zgłoś błąd lub sugestię")) },
        text = {
            if (sent) {
                Text(tr("Dziękuję! Zgłoszenie dotarło."))
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !feature, onClick = { feature = false }, label = { Text(tr("Błąd")) })
                        FilterChip(selected = feature, onClick = { feature = true }, label = { Text(tr("Sugestia")) })
                    }
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it.take(8000); error = null },
                        placeholder = {
                            Text(if (feature) tr("Czego brakuje albo co można zrobić lepiej?") else tr("Co się stało? Co robiłeś tuż przed błędem?"))
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        enabled = !sending,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.take(200) },
                        label = { Text(tr("E-mail (opcjonalnie)")) },
                        supportingText = { Text(tr("Tylko jeśli chcesz dostać odpowiedź")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !sending,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = diagnostics, onCheckedChange = { diagnostics = it }, enabled = !sending)
                        Text(tr("Dołącz wersję aplikacji i model urządzenia"), style = MaterialTheme.typography.bodySmall)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text(tr("Zamknij")) }
            } else {
                TextButton(
                    enabled = !sending,
                    onClick = {
                        if (message.trim().length < 3) {
                            error = tr("Opisz problem w kilku słowach.")
                            return@TextButton
                        }
                        sending = true
                        scope.launch {
                            Feedback.send(platform, if (feature) "feature" else "bug", message, email, diagnostics)
                                .onSuccess { sent = true }
                                .onFailure { error = tr("Nie udało się wysłać. Sprawdź połączenie i spróbuj ponownie.") }
                            sending = false
                        }
                    },
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(tr("Wyślij"))
                }
            }
        },
        dismissButton = { if (!sent) TextButton(onClick = onDismiss, enabled = !sending) { Text(tr("Anuluj")) } },
    )
}

/** Pytanie o wysłanie raportu po awarii zapisanej przez [Feedback.recordCrash]. */
@Composable
fun CrashReportPrompt(platform: Platform) {
    val scope = rememberCoroutineScope()
    var crash by remember { mutableStateOf<Pair<String, String>?>(null) }
    var comment by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var details by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { crash = Feedback.pendingCrash(platform) }
    val (version, stack) = crash ?: return

    AlertDialog(
        onDismissRequest = {},
        title = { Text(tr("Aplikacja zamknęła się z błędem")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Ostatnio aplikacja zakończyła działanie przez błąd. Wysłać autorowi raport? Zawiera opis błędu, wersję aplikacji i model urządzenia – bez Twoich danych."))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(4000) },
                    placeholder = { Text(tr("Co robiłeś przed awarią? (opcjonalnie)")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sending,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.take(200) },
                    label = { Text(tr("E-mail (opcjonalnie)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sending,
                )
                TextButton(onClick = { details = !details }) { Text(if (details) tr("Ukryj szczegóły") else tr("Pokaż szczegóły")) }
                if (details) Text(stack.lines().take(40).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending,
                onClick = {
                    sending = true
                    scope.launch {
                        Feedback.send(platform, "crash", comment, email, stack = stack, version = version)
                            .onSuccess { Feedback.discardCrash(platform); crash = null }
                            .onFailure { error = tr("Nie udało się wysłać. Sprawdź połączenie i spróbuj ponownie.") }
                        sending = false
                    }
                },
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(tr("Wyślij raport"))
            }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = { Feedback.discardCrash(platform); crash = null }) { Text(tr("Nie wysyłaj")) }
        },
    )
}
