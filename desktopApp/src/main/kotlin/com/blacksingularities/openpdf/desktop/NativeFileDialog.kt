package com.blacksingularities.openpdf.desktop

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.Window
import java.io.File

/**
 * Systemowe okno otwierania plików Windows (IFileOpenDialog) – nowoczesny wygląd Eksploratora,
 * wybór wielu plików i filtry. COM wołany bezpośrednio przez tablice metod (JNA).
 */
object NativeFileDialog {
    private val CLSID_FileOpenDialog = Guid.CLSID("{DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7}")
    private val IID_IFileOpenDialog = Guid.IID("{d57c7288-d4ad-4768-be02-9d969532d960}")
    private val IID_IShellItem = Guid.IID("{43826d1e-e718-42ee-bc55-a1e261c37bfe}")

    private const val CLSCTX_INPROC_SERVER = 1
    private const val COINIT_APARTMENTTHREADED = 2
    private const val FOS_FORCEFILESYSTEM = 0x40
    private const val FOS_ALLOWMULTISELECT = 0x200
    private const val FOS_PATHMUSTEXIST = 0x800
    private const val FOS_FILEMUSTEXIST = 0x1000
    private const val SIGDN_FILESYSPATH = 0x80058000.toInt()

    // Numery metod w tablicach wirtualnych.
    private const val SHOW = 3
    private const val SET_FILE_TYPES = 4
    private const val GET_OPTIONS = 10
    private const val SET_OPTIONS = 9
    private const val SET_FOLDER = 12
    private const val SET_TITLE = 17
    private const val GET_RESULTS = 27
    private const val ARRAY_GET_COUNT = 7
    private const val ARRAY_GET_ITEM = 8
    private const val ITEM_GET_DISPLAY_NAME = 5
    private const val RELEASE = 2

    class Filter(val name: String, val patterns: List<String>)

    /**
     * Wybór plików; blokuje bieżący wątek (wywoływać poza wątkiem interfejsu).
     * Zwraca null, gdy okno systemowe nie jest dostępne – wtedy należy użyć zapasowego.
     */
    fun open(owner: Window?, title: String, filters: List<Filter>, folder: File?): List<File>? {
        if (!System.getProperty("os.name").startsWith("Windows")) return null
        return runCatching { openImpl(owner, title, filters, folder) }.getOrNull()
    }

    private fun call(obj: Pointer, index: Int, vararg args: Any?): Int {
        val vtbl = obj.getPointer(0)
        val fn = Function.getFunction(vtbl.getPointer(index.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
        return fn.invokeInt(arrayOf<Any?>(obj, *args))
    }

    private fun wide(s: String): Memory {
        val m = Memory((s.length + 1L) * Native.WCHAR_SIZE)
        m.setWideString(0, s)
        return m
    }

    private fun openImpl(owner: Window?, title: String, filters: List<Filter>, folder: File?): List<File> {
        val init = Ole32.INSTANCE.CoInitializeEx(null, COINIT_APARTMENTTHREADED)
        try {
            val ref = PointerByReference()
            val hr = Ole32.INSTANCE.CoCreateInstance(CLSID_FileOpenDialog, null, CLSCTX_INPROC_SERVER, IID_IFileOpenDialog, ref)
            if (hr.toInt() != 0) error("CoCreateInstance $hr")
            val dlg = ref.value
            val keep = ArrayList<Memory>()
            try {
                val opts = IntByReference()
                call(dlg, GET_OPTIONS, opts)
                call(dlg, SET_OPTIONS, opts.value or FOS_FORCEFILESYSTEM or FOS_ALLOWMULTISELECT or FOS_PATHMUSTEXIST or FOS_FILEMUSTEXIST)
                call(dlg, SET_TITLE, wide(title).also { keep += it })
                if (filters.isNotEmpty()) {
                    val spec = Memory(filters.size * 2L * Native.POINTER_SIZE)
                    filters.forEachIndexed { i, f ->
                        val n = wide(f.name).also { keep += it }
                        val p = wide(f.patterns.joinToString(";")).also { keep += it }
                        spec.setPointer(i * 2L * Native.POINTER_SIZE, n)
                        spec.setPointer((i * 2L + 1) * Native.POINTER_SIZE, p)
                    }
                    keep += spec
                    call(dlg, SET_FILE_TYPES, filters.size, spec)
                }
                if (folder != null && folder.isDirectory) {
                    val item = PointerByReference()
                    val r = Shell32Ext.INSTANCE.SHCreateItemFromParsingName(com.sun.jna.WString(folder.absolutePath), null, IID_IShellItem, item)
                    if (r == 0 && item.value != null) {
                        call(dlg, SET_FOLDER, item.value)
                        call(item.value, RELEASE)
                    }
                }
                val hwnd = owner?.let { Native.getWindowPointer(it) }
                val shown = call(dlg, SHOW, hwnd)
                if (shown != 0) return emptyList() // anulowano
                val arr = PointerByReference()
                if (call(dlg, GET_RESULTS, arr) != 0 || arr.value == null) return emptyList()
                val out = ArrayList<File>()
                try {
                    val count = IntByReference()
                    call(arr.value, ARRAY_GET_COUNT, count)
                    for (i in 0 until count.value) {
                        val item = PointerByReference()
                        if (call(arr.value, ARRAY_GET_ITEM, i, item) != 0 || item.value == null) continue
                        val name = PointerByReference()
                        if (call(item.value, ITEM_GET_DISPLAY_NAME, SIGDN_FILESYSPATH, name) == 0 && name.value != null) {
                            out += File(name.value.getWideString(0))
                            Ole32.INSTANCE.CoTaskMemFree(name.value)
                        }
                        call(item.value, RELEASE)
                    }
                } finally {
                    call(arr.value, RELEASE)
                }
                return out
            } finally {
                call(dlg, RELEASE)
                keep.clear()
            }
        } finally {
            if (init.toInt() == 0 || init.toInt() == 1) Ole32.INSTANCE.CoUninitialize()
        }
    }

    private interface Shell32Ext : com.sun.jna.win32.StdCallLibrary {
        @Suppress("FunctionName")
        fun SHCreateItemFromParsingName(path: com.sun.jna.WString, bc: Pointer?, riid: Guid.GUID, out: PointerByReference): Int

        companion object {
            val INSTANCE: Shell32Ext = Native.load("shell32", Shell32Ext::class.java, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS)
        }
    }
}
