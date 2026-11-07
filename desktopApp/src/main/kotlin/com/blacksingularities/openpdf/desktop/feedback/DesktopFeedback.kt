package com.blacksingularities.openpdf.desktop.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale

/**
 * Zgłoszenia do redzikowski.com z aplikacji na Windows:
 *  - raport o błędzie: awaria zapisana przez [install], proponowana do wysłania przy
 *    następnym starcie (→ „Raporty błędów” w panelu),
 *  - zgłoszenie błędu albo sugestia funkcjonalności (→ „Sugestie i zgłoszenia”).
 * E-mail jest opcjonalny; nic nie wychodzi bez decyzji użytkownika.
 */
object DesktopFeedback {
    private const val ENDPOINT = "https://redzikowski.com/api/feedback"

    var slug = ""
        private set
    var appName = ""
        private set
    var version = ""
        private set
    private var dir: File? = null

    /** [dataDir] – katalog danych programu (tam trafia zapis ostatniej awarii). */
    fun install(slug: String, appName: String, version: String, dataDir: File) {
        this.slug = slug
        this.appName = appName
        this.version = version
        this.dir = dataDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(error, thread.name)
            previous?.uncaughtException(thread, error)
        }
    }

    private fun crashFile() = dir?.let { File(it, "feedback-pending-crash.txt") }

    /** Zapis awarii – także takiej przechwyconej, po której program się zamyka. */
    fun record(error: Throwable, where: String = "") {
        runCatching {
            val file = crashFile() ?: return
            file.parentFile?.mkdirs()
            val sw = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
            file.writeText("$version\n" + (if (where.isNotBlank()) "Wątek: $where\n" else "") + sw.toString().take(60_000))
        }
    }

    /** Wersja i stos ostatniej awarii albo null. */
    fun pendingCrash(): Pair<String, String>? = runCatching {
        val file = crashFile()?.takeIf { it.isFile } ?: return null
        val text = file.readText()
        text.substringBefore('\n') to text.substringAfter('\n')
    }.getOrNull()

    fun discardCrash() {
        runCatching { crashFile()?.delete() }
    }

    private fun esc(value: String) = buildString {
        for (c in value) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }

    suspend fun send(
        kind: String,
        message: String,
        email: String,
        diagnostics: Boolean = true,
        stack: String = "",
        appVersion: String = version,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fields = linkedMapOf(
                "kind" to kind,
                "app" to slug,
                "appName" to appName,
                "appVersion" to appVersion,
                "platform" to "windows",
                "locale" to Locale.getDefault().toLanguageTag(),
                "message" to message.trim(),
                "email" to email.trim(),
            )
            if (stack.isNotBlank()) fields["stack"] = stack
            if (diagnostics || kind == "crash") {
                fields["os"] = "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})"
                fields["device"] = "Java ${System.getProperty("java.version")} · ${Runtime.getRuntime().availableProcessors()} CPU · ${Runtime.getRuntime().maxMemory() / 1_048_576} MB"
            }
            val body = fields.entries.joinToString(",", "{", "}") { (k, v) -> "\"${esc(k)}\":\"${esc(v)}\"" }
            val request = HttpRequest.newBuilder(URI(ENDPOINT))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
            val code = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
            if (code !in 200..299) error("HTTP $code")
        }
    }
}

private class FeedbackTexts(pl: Boolean) {
    val title = if (pl) "Zgłoś błąd lub sugestię" else "Report a bug or suggest a feature"
    val bug = if (pl) "Błąd" else "Bug"
    val feature = if (pl) "Sugestia" else "Suggestion"
    val bugHint = if (pl) "Co się stało? Co robiłeś tuż przed błędem?" else "What happened? What were you doing right before?"
    val featureHint = if (pl) "Czego brakuje albo co można zrobić lepiej?" else "What is missing or could work better?"
    val email = if (pl) "E-mail (opcjonalnie)" else "Email (optional)"
    val emailHint = if (pl) "Tylko jeśli chcesz dostać odpowiedź" else "Only if you'd like a reply"
    val diagnostics = if (pl) "Dołącz wersję programu i dane systemu" else "Include program version and system details"
    val send = if (pl) "Wyślij" else "Send"
    val cancel = if (pl) "Anuluj" else "Cancel"
    val close = if (pl) "Zamknij" else "Close"
    val thanks = if (pl) "Dziękuję! Zgłoszenie dotarło." else "Thank you! Your report was sent."
    val failed = if (pl) "Nie udało się wysłać. Sprawdź połączenie i spróbuj ponownie." else "Couldn't send. Check your connection and try again."
    val tooShort = if (pl) "Opisz problem w kilku słowach." else "Please describe it in a few words."
    val crashTitle = if (pl) "Program zamknął się z błędem" else "The program closed unexpectedly"
    val crashBody = if (pl) "Ostatnio program zakończył działanie przez błąd. Wysłać autorowi raport? Zawiera opis błędu, wersję programu i wersję systemu – bez Twoich plików." else "The program recently stopped because of an error. Send a report to the developer? It contains the error details, program version and system version – none of your files."
    val crashComment = if (pl) "Co robiłeś przed awarią? (opcjonalnie)" else "What were you doing? (optional)"
    val showDetails = if (pl) "Pokaż szczegóły" else "Show details"
    val hideDetails = if (pl) "Ukryj szczegóły" else "Hide details"
    val copy = if (pl) "Kopiuj" else "Copy"
    val sendReport = if (pl) "Wyślij raport" else "Send report"
    val dontSend = if (pl) "Nie wysyłaj" else "Don't send"
}

private fun systemPolish() = Locale.getDefault().language == "pl"

/** Formularz zgłoszenia błędu albo sugestii. */
@Composable
fun FeedbackDialog(onDismiss: () -> Unit, polish: Boolean = systemPolish()) {
    val t = remember(polish) { FeedbackTexts(polish) }
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
        title = { Text(t.title) },
        text = {
            if (sent) {
                Text(t.thanks)
            } else {
                Column(Modifier.width(460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !feature, onClick = { feature = false }, label = { Text(t.bug) })
                        FilterChip(selected = feature, onClick = { feature = true }, label = { Text(t.feature) })
                    }
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it.take(8000); error = null },
                        placeholder = { Text(if (feature) t.featureHint else t.bugHint) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                        enabled = !sending,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.take(200) },
                        label = { Text(t.email) },
                        supportingText = { Text(t.emailHint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !sending,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = diagnostics, onCheckedChange = { diagnostics = it }, enabled = !sending)
                        Text(t.diagnostics, style = MaterialTheme.typography.bodySmall)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text(t.close) }
            } else {
                TextButton(
                    enabled = !sending,
                    onClick = {
                        if (message.trim().length < 3) {
                            error = t.tooShort
                            return@TextButton
                        }
                        sending = true
                        scope.launch {
                            DesktopFeedback.send(if (feature) "feature" else "bug", message, email, diagnostics)
                                .onSuccess { sent = true }
                                .onFailure { error = t.failed }
                            sending = false
                        }
                    },
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(t.send)
                }
            }
        },
        dismissButton = { if (!sent) TextButton(onClick = onDismiss, enabled = !sending) { Text(t.cancel) } },
    )
}

/** Pytanie o wysłanie raportu po awarii z poprzedniego uruchomienia. Umieść raz w oknie głównym. */
@Composable
fun CrashReportPrompt(polish: Boolean = systemPolish()) {
    val t = remember(polish) { FeedbackTexts(polish) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var crash by remember { mutableStateOf<Pair<String, String>?>(null) }
    var comment by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var details by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { crash = DesktopFeedback.pendingCrash() }
    val (version, stack) = crash ?: return

    AlertDialog(
        onDismissRequest = {},
        title = { Text(t.crashTitle) },
        text = {
            Column(Modifier.width(500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t.crashBody)
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(4000) },
                    placeholder = { Text(t.crashComment) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sending,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.take(200) },
                    label = { Text(t.email) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sending,
                )
                Row {
                    TextButton(onClick = { details = !details }) { Text(if (details) t.hideDetails else t.showDetails) }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(stack)) }) { Text(t.copy) }
                }
                if (details) Text(stack.lines().take(40).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending,
                onClick = {
                    sending = true
                    scope.launch {
                        DesktopFeedback.send("crash", comment, email, stack = stack, appVersion = version)
                            .onSuccess { DesktopFeedback.discardCrash(); crash = null }
                            .onFailure { error = t.failed }
                        sending = false
                    }
                },
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(t.sendReport)
            }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = { DesktopFeedback.discardCrash(); crash = null }) { Text(t.dontSend) }
        },
    )
}
