// Aktualizacje aplikacji.

package com.blacksingularities.openpdf.ui

import com.blacksingularities.openpdf.i18n.tr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blacksingularities.openpdf.platform.Platform
import com.blacksingularities.openpdf.platform.ioDispatcher
import com.blacksingularities.openpdf.platform.nowMillis
import com.blacksingularities.openpdf.update.UPDATE_API
import com.blacksingularities.openpdf.update.UpdateState
import com.blacksingularities.openpdf.update.isNewer
import com.blacksingularities.openpdf.update.parseRelease
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope

/** Sprawdzanie, pobieranie i instalacja nowej wersji z GitHub Releases. */
class UpdateController(private val platform: Platform, private val scope: CoroutineScope, private val autoUpdate: () -> Boolean) {
    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    private var updateJob: Job? = null

    fun checkUpdate(silent: Boolean = false) {
        if (updateJob?.isActive == true) return
        updateJob = scope.launch {
            update = UpdateState.Checking
            update = try {
                val info = withContext(ioDispatcher) { parseRelease(platform.httpGet(UPDATE_API), platform.updateAsset) }
                if (isNewer(info.version, platform.appVersion)) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: Throwable) {
                if (silent) UpdateState.Idle else UpdateState.Failed(e.message ?: tr("Brak połączenia"))
            }
        }
    }

    fun downloadUpdate(installAfter: Boolean = false) {
        val info = (update as? UpdateState.Available)?.info ?: return
        updateJob = scope.launch {
            update = UpdateState.Downloading(info, 0f)
            update = try {
                val file = platform.downloadUpdate(info) { p ->
                    scope.launch { if (update is UpdateState.Downloading) update = UpdateState.Downloading(info, p) }
                }
                UpdateState.Ready(info, file)
            } catch (e: Throwable) {
                UpdateState.Failed(e.message ?: tr("Pobieranie nie powiodło się"))
            }
            if (installAfter && update is UpdateState.Ready) installUpdate()
        }
    }

    /** Jednym dotknięciem: pobranie nowej wersji z GitHuba i instalacja. */
    fun updateNow() {
        val u = update
        if (!platform.canSelfUpdate && u is UpdateState.Available) {
            // iOS: tylko komunikat o nowej wersji – bez linków i listy zmian.
            return
        }
        when (u) {
            is UpdateState.Available -> downloadUpdate(installAfter = true)
            is UpdateState.Ready -> installUpdate()
            is UpdateState.Failed, UpdateState.Idle, UpdateState.UpToDate -> checkUpdate()
            else -> Unit
        }
    }

    /** Baner nowej wersji ukryty przez użytkownika (do następnego uruchomienia). */
    var updateBannerHidden by mutableStateOf(false)

    private var lastUpdateCheck = 0L

    /** Ponowne sprawdzenie po powrocie do aplikacji – najwyżej co 6 godzin. */
    fun checkUpdateIfDue() {
        if (!autoUpdate()) return
        val now = nowMillis()
        if (now - lastUpdateCheck < 6 * 3600_000L) return
        if (update is UpdateState.Downloading || update is UpdateState.Ready || update is UpdateState.Installing) return
        lastUpdateCheck = now
        checkUpdate(silent = true)
    }

    fun installUpdate() {
        val ready = update as? UpdateState.Ready ?: return
        if (!platform.canInstallUpdates()) {
            platform.openInstallPermissionSettings()
            return
        }
        scope.launch {
            update = UpdateState.Installing(ready.info)
            try {
                platform.installUpdate(ready.file)
            } catch (e: Throwable) {
                update = UpdateState.Failed(e.message ?: tr("Instalacja nie powiodła się"))
            }
        }
    }
}
