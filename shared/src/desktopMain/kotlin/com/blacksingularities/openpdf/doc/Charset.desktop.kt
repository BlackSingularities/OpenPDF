package com.blacksingularities.openpdf.doc

import java.nio.charset.Charset

actual fun decodeCharset(bytes: ByteArray, charset: String): String? =
    runCatching { String(bytes, Charset.forName(charset)) }.getOrNull()
