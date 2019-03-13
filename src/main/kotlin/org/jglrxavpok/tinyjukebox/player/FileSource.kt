package org.jglrxavpok.tinyjukebox.player

import java.io.File
import java.io.InputStream

class FileSource(val file: File): MusicSource {
    override fun createStream(): InputStream = file.inputStream()
}