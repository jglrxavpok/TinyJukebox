package org.jglrxavpok.tinyjukebox.player

import java.io.InputStream

import kotlinx.coroutines.*

/**
 * Timeout-able InputStream
 * Requires a base InputStream and a timeout in milliseconds
 */
class TimeoutInputStream(val baseInput: InputStream, val timeout: Long): InputStream() {
    override fun read(): Int {
        return runBlocking {
            withTimeout(timeout) {
                baseInput.read()
            }
        }
    }

    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        return runBlocking { withTimeout(timeout) { baseInput.read(b, off, len) } }
    }
}