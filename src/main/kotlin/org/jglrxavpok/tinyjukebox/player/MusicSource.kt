package org.jglrxavpok.tinyjukebox.player

import java.io.InputStream

interface MusicSource {
    fun createStream(): InputStream
    fun computeDurationInMillis(): Long
}