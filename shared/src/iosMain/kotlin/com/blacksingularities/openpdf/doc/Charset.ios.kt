package com.blacksingularities.openpdf.doc

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.*

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun decodeCharset(bytes: ByteArray, charset: String): String? {
    val enc: NSStringEncoding = when (charset.lowercase().replace("_", "-")) {
        "utf-8", "utf8" -> NSUTF8StringEncoding
        "utf-16" -> NSUTF16StringEncoding
        "utf-16le" -> NSUTF16LittleEndianStringEncoding
        "utf-16be" -> NSUTF16BigEndianStringEncoding
        "windows-1250", "cp1250", "x-cp1250" -> NSWindowsCP1250StringEncoding
        "windows-1251", "cp1251", "x-cp1251" -> NSWindowsCP1251StringEncoding
        "windows-1252", "cp1252", "x-cp1252" -> NSWindowsCP1252StringEncoding
        "iso-8859-1", "latin1", "iso8859-1" -> NSISOLatin1StringEncoding
        "iso-8859-2", "latin2", "iso8859-2" -> NSISOLatin2StringEncoding
        else -> return null
    }
    if (bytes.isEmpty()) return ""
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    return NSString.create(data = data, encoding = enc) as String?
}
