package org.jglrxavpok.tinyjukebox.player

import java.io.File
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

class FileSource(val file: File): MusicSource {
    override fun computeDurationInMillis(): Long {
        val length = file.length()
        val fileFormat = AudioSystem.getAudioFileFormat(file)
        val format = fileFormat.format

        val decodedFormat = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            format.getSampleRate(),
            16,
            format.getChannels(),
            format.getChannels() * 2,
            format.getSampleRate(),
            false
        )
        val frameCount = fileFormat.frameLength//length.toDouble()/decodedFormat.frameSize
        val duration = (1000.0*frameCount/format.frameRate).toLong()
        println(">> duration=${duration/1000.0}")
        return duration
    }

    override fun createStream(): InputStream = file.inputStream()
}