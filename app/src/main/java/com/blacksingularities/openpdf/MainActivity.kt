package com.blacksingularities.openpdf

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.blacksingularities.openpdf.ui.AppRoot
import com.blacksingularities.openpdf.ui.PdfViewModel
import com.blacksingularities.openpdf.ui.Screen
import com.blacksingularities.openpdf.ui.theme.OpenPdfTheme
import com.blacksingularities.openpdf.update.UpdateManager
import android.widget.Toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val vm: PdfViewModel by viewModels {
        viewModelFactory { initializer { PdfViewModel(AndroidPlatform(application)) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        UpdateManager.clearUpdatedNotification(this)
        vm.applyLanguage()

        // Rozłożony fold (pionowy zawias) albo pozycja „książki” – sygnał dla automatycznego trybu książki.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity).collect { info ->
                    val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                    vm.foldBook = fold != null && fold.orientation == FoldingFeature.Orientation.VERTICAL
                }
            }
        }

        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            OpenPdfTheme {
                val keepOn = vm.settings.keepScreenOn && vm.screen != Screen.HOME
                LaunchedEffect(keepOn) {
                    if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                // Ukryty pasek stanu w czytniku – pojawia się po przesunięciu palcem od góry.
                val hideBars = vm.settings.hideStatusBar && vm.screen != Screen.HOME
                LaunchedEffect(hideBars) {
                    val c = WindowCompat.getInsetsController(window, window.decorView)
                    c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (hideBars) c.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    else c.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                }
                ProvideAndroidPlatformUi(vm) { AppRoot(vm) }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Zmiana języka systemu bez restartu ekranu.
        vm.applyLanguage()
    }

    override fun onResume() {
        super.onResume()
        vm.checkUpdateIfDue()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        if (uri == null) return
        if (uri.scheme == "http" || uri.scheme == "https") openRemote(uri) else vm.open(uri.toString())
    }

    /** Pobiera dokument z linku do cache i otwiera go jak zwykły plik. */
    private fun openRemote(uri: Uri) {
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    var url = java.net.URL(uri.toString())
                    var conn: java.net.HttpURLConnection
                    var hops = 0
                    while (true) {
                        conn = url.openConnection() as java.net.HttpURLConnection
                        conn.instanceFollowRedirects = true
                        conn.connectTimeout = 15000
                        conn.readTimeout = 30000
                        val code = conn.responseCode
                        // Przekierowania między http a https nie są obsługiwane automatycznie.
                        if (code in 300..399 && hops++ < 5) {
                            val loc = conn.getHeaderField("Location") ?: break
                            url = java.net.URL(url, loc)
                            conn.disconnect()
                            continue
                        }
                        if (code !in 200..299) error("HTTP $code")
                        break
                    }
                    val name = fileName(conn, url)
                    val dir = File(cacheDir, "downloads").apply { mkdirs() }
                    val out = File(dir, name)
                    conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
                    conn.disconnect()
                    out
                }.getOrNull()
            }
            if (file != null) vm.open(Uri.fromFile(file).toString())
            else Toast.makeText(this@MainActivity, "Nie udało się pobrać pliku", Toast.LENGTH_LONG).show()
        }
    }

    private fun fileName(conn: java.net.HttpURLConnection, url: java.net.URL): String {
        val cd = conn.getHeaderField("Content-Disposition")
        val fromHeader = cd?.let { Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)").find(it)?.groupValues?.get(1) }
            ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        var name = (fromHeader ?: url.path.substringAfterLast('/')).replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (name.isBlank()) name = "dokument"
        if (!name.contains('.')) {
            val ext = android.webkit.MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(conn.contentType?.substringBefore(';')?.trim()) ?: "pdf"
            name += ".$ext"
        }
        return name
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val reading = vm.screen == Screen.VIEWER && vm.render != null && vm.sheet == null
        if (reading && vm.settings.volumeKeys && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            vm.pageStep(if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1)
            return true
        }
        if (reading) {
            when (keyCode) {
                KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SPACE -> {
                    vm.pageStep(1)
                    return true
                }
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                    vm.pageStep(-1)
                    return true
                }
                KeyEvent.KEYCODE_S -> if (event.isCtrlPressed) {
                    vm.save()
                    return true
                }
                KeyEvent.KEYCODE_Z -> if (event.isCtrlPressed) {
                    if (event.isShiftPressed) vm.redo() else vm.undo()
                    return true
                }
                KeyEvent.KEYCODE_F -> if (event.isCtrlPressed) {
                    vm.searchActive = true
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
