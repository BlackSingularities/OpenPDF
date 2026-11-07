package com.blacksingularities.openpdf.ios

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.blacksingularities.openpdf.feedback.CrashReportPrompt
import com.blacksingularities.openpdf.feedback.Feedback
import com.blacksingularities.openpdf.platform.LocalPlatformUi
import com.blacksingularities.openpdf.ui.AppRoot
import com.blacksingularities.openpdf.ui.PdfViewModel
import com.blacksingularities.openpdf.ui.Screen
import com.blacksingularities.openpdf.ui.theme.OpenPdfTheme
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/** Jeden model widoku na cały czas życia aplikacji. */
private object IosApp {
    val vm: PdfViewModel by lazy {
        val platform = IosPlatform()
        installCrashHook(platform)
        PdfViewModel(platform)
    }
}

/** Nieprzechwycony wyjątek Kotlina: zapis do pliku, raport proponowany przy następnym starcie. */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
private fun installCrashHook(platform: IosPlatform) {
    var previous: ((Throwable) -> Unit)? = null
    previous = kotlin.native.setUnhandledExceptionHook { error ->
        Feedback.recordCrash(platform, error.stackTraceToString())
        previous?.invoke(error)
    }
}

/** Główny ekran aplikacji – wywoływany ze Swifta. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    val vm = IosApp.vm
    val ui = remember { IosPlatformUi(vm) }
    val keepOn = vm.settings.keepScreenOn && vm.screen != Screen.HOME
    LaunchedEffect(keepOn) { UIApplication.sharedApplication.idleTimerDisabled = keepOn }
    CompositionLocalProvider(LocalPlatformUi provides ui) {
        OpenPdfTheme {
            AppRoot(vm)
            CrashReportPrompt(vm.platform)
        }
    }
}

/** Plik otwarty z innej aplikacji („Otwórz w…”, Pliki). */
fun openUrl(url: NSURL) {
    IosApp.vm.open(bookmarkOf(url))
}

/** Powrót aplikacji na pierwszy plan. */
fun onResume() {
    IosApp.vm.applyLanguage()
    IosApp.vm.updates.checkUpdateIfDue()
}
