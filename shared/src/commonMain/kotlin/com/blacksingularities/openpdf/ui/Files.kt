// Pomocnicze operacje na plikach (okio).

package com.blacksingularities.openpdf.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.blacksingularities.openpdf.platform.fileSystem
import okio.Path

internal fun Path.delete(): Boolean = runCatching { fileSystem.delete(this) }.isSuccess

internal fun Path.copyTo(target: Path): Path {
    fileSystem.copy(this, target)
    return target
}

internal fun Path.length(): Long = fileSystem.metadataOrNull(this)?.size ?: 0L

internal fun Path.deleteRecursively() = runCatching { fileSystem.deleteRecursively(this) }

internal fun Path.mkdirs(): Path = also { fileSystem.createDirectories(it) }

internal fun Path.renameTo(target: Path): Boolean = runCatching { fileSystem.atomicMove(this, target) }.isSuccess

internal fun Path.listFiles(): List<Path>? = fileSystem.listOrNull(this)
