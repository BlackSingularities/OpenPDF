package com.blacksingularities.openpdf.desktop

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window

/**
 * Okno bez systemowej ramki (karty leżą w pasku tytułu): zaokrąglone rogi Windows 11,
 * minimalizacja kliknięciem w pasek zadań i maksymalizacja do obszaru roboczego (bez zasłaniania paska zadań).
 */
object WindowsChrome {
    private val isWindows = System.getProperty("os.name").startsWith("Windows")

    @Suppress("FunctionName")
    private interface Dwm : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: IntByReference, size: Int): Int
    }

    private val dwm: Dwm? by lazy { runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull() }

    private const val GWL_STYLE = -16
    private const val WS_MINIMIZEBOX = 0x00020000
    private const val WS_MAXIMIZEBOX = 0x00010000
    private const val WS_SYSMENU = 0x00080000
    private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
    private const val DWMWCP_ROUND = 2

    fun install(window: Window) {
        if (!isWindows) return
        runCatching {
            val hwnd = WinDef.HWND(Native.getWindowPointer(window))
            val style = User32.INSTANCE.GetWindowLong(hwnd, GWL_STYLE)
            User32.INSTANCE.SetWindowLong(hwnd, GWL_STYLE, style or WS_MINIMIZEBOX or WS_MAXIMIZEBOX or WS_SYSMENU)
            dwm?.DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, IntByReference(DWMWCP_ROUND), 4)
        }
        updateMaximizedBounds(window)
        window.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentMoved(e: java.awt.event.ComponentEvent) = updateMaximizedBounds(window)
        })
    }

    /** Obszar maksymalizacji: ekran okna bez paska zadań (współrzędne względem monitora, jak oczekuje WM_GETMINMAXINFO). */
    fun updateMaximizedBounds(window: Window) {
        val frame = window as? java.awt.Frame ?: return
        val gc = window.graphicsConfiguration ?: return
        val b = gc.bounds
        val ins = Toolkit.getDefaultToolkit().getScreenInsets(gc)
        frame.maximizedBounds = Rectangle(ins.left, ins.top, b.width - ins.left - ins.right, b.height - ins.top - ins.bottom)
    }
}
