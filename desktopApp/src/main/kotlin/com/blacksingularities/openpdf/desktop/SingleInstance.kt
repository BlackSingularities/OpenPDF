package com.blacksingularities.openpdf.desktop

import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Jedno okno programu: kolejne uruchomienie (np. dwuklik pliku w Eksploratorze) przekazuje
 * ścieżki do działającej instancji przez lokalne gniazdo i kończy się.
 */
object SingleInstance {
    private const val HELLO = "OpenDocument/1"

    fun forward(files: List<File>): Boolean {
        val port = runCatching { AppDirs.instance.readText().trim().toInt() }.getOrNull() ?: return false
        return runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 700)
                s.soTimeout = 1500
                val out = s.getOutputStream().bufferedWriter(Charsets.UTF_8)
                out.write(HELLO + "\n")
                files.forEach { out.write(it.absolutePath + "\n") }
                out.write("\n")
                out.flush()
                s.getInputStream().bufferedReader(Charsets.UTF_8).readLine() == "OK"
            }
        }.getOrDefault(false)
    }

    fun listen(onFiles: (List<File>) -> Unit) {
        val server = runCatching { ServerSocket(0, 10, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        runCatching { AppDirs.instance.writeText(server.localPort.toString()) }
        Thread({
            while (true) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    s.use {
                        it.soTimeout = 2000
                        val r = it.getInputStream().bufferedReader(Charsets.UTF_8)
                        if (r.readLine() != HELLO) return@use
                        val files = ArrayList<File>()
                        while (true) {
                            val line = r.readLine() ?: break
                            if (line.isEmpty()) break
                            files += File(line)
                        }
                        it.getOutputStream().write("OK\n".toByteArray())
                        it.getOutputStream().flush()
                        onFiles(files.filter { f -> f.exists() })
                    }
                }
            }
        }, "single-instance").apply { isDaemon = true }.start()
    }
}
