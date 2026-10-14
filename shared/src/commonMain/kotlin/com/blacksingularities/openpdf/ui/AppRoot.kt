package com.blacksingularities.openpdf.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blacksingularities.openpdf.platform.LocalPlatformUi
import com.blacksingularities.openpdf.ui.theme.GlassScope

@Composable
fun AppRoot(vm: PdfViewModel) {
    val ui = LocalPlatformUi.current
    LaunchedEffect(vm) {
        vm.uiEvents.collect { e ->
            when (e) {
                is UiEvent.AskSaveLocation -> ui.saveAs(e.name, e.mime)
                is UiEvent.Share -> ui.share(e.file, e.mime)
                is UiEvent.Print -> ui.print(e.file, e.name)
                is UiEvent.OpenUri -> ui.openUri(e.uri)
                UiEvent.PickImageForPlacement -> ui.pickImageForPlacement()
            }
        }
    }
    GlassScope(haptics = vm.settings.haptics) {
        when (vm.screen) {
            Screen.HOME -> HomeScreen(vm)
            Screen.VIEWER -> if (vm.render != null) ViewerScreen(vm) else HomeScreen(vm)
            Screen.PAGES -> if (vm.render != null) PagesScreen(vm) else HomeScreen(vm)
        }
        ViewerSheets(vm)
        ViewModelDialogs(vm)
        BusyOverlay(vm)
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 90.dp), contentAlignment = Alignment.BottomCenter) {
            MessagePill(vm)
        }
    }
}
