package com.rasha.omni.common

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads at most [max] bytes. (InputStream.readNBytes does not exist before Android 13, so it must not be used on these TVs.) */
fun InputStream.readUpTo(max: Int): ByteArray {
    val out = ByteArrayOutputStream(minOf(max, 16384)); val buf = ByteArray(8192); var left = max
    while (left > 0) { val n = read(buf, 0, minOf(buf.size, left)); if (n < 0) break; out.write(buf, 0, n); left -= n }
    return out.toByteArray()
}
