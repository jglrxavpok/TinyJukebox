package org.jglrxavpok.tinyjukebox.player

import org.jglrxavpok.tinyjukebox.Music
import org.jglrxavpok.tinyjukebox.TinyJukebox
import java.io.BufferedInputStream
import java.io.FileInputStream
import javax.sound.sampled.*
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.DataLine

class State(var currentMusic: Music? = null, var startTime: Long = 0L, var duration: Long = 1L) {
    fun isPlaying() = currentMusic != null

    fun setPlaying(music: Music?, startTime: Long, duration: Long) {
        this.currentMusic = music
        this.startTime = startTime
        this.duration = duration
    }
}

object MusicPlayer: Thread("Music Player") {

    var bytesRead: Long = 0
    var state = State()

    override fun run() {
        while(!Thread.currentThread().isInterrupted) {
            if(!state.isPlaying()) {
                val music = TinyJukebox.pollQueue()
                try {
                    if(music != null) {
                        state.setPlaying(music, System.currentTimeMillis(), music.source.computeDurationInMillis())
                        println(">> Playing ${music.name} / ${music.source}")
                        val unbufferedStream = music.source.createStream()
                        val available = unbufferedStream.available()
                        val sourceStream = BufferedInputStream(unbufferedStream)
                        val format = AudioSystem.getAudioFileFormat(sourceStream)
                        println("format is $format")
                        val input = AudioSystem.getAudioInputStream(sourceStream)
                        println("input read")
                        val baseFormat = input.format
                        println("frameLength: ${input.frameLength} - ${available/input.format.frameSize}")
                        println("format: ${input.format}")
                        println("base format read")
                        val decodedFormat = AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            baseFormat.getSampleRate(),
                            16,
                            baseFormat.getChannels(),
                            baseFormat.getChannels() * 2,
                            baseFormat.getSampleRate(),
                            false
                        )
                        val din = AudioSystem.getAudioInputStream(decodedFormat, input)

                        println("din frameLength: ${din.frameLength} - ${available/din.format.frameSize}")
                        println("din format: ${din.format}")

                        /*println("pre clip opening")
                        clip.open(din)
                        println("clip opening")
                        clip.start()
                        println("clip start")*/
                        val dataLineInfo = DataLine.Info(SourceDataLine::class.java, decodedFormat)
                        val sourceDataLine = AudioSystem.getLine(dataLineInfo) as SourceDataLine
                        sourceDataLine.open(decodedFormat)
                        sourceDataLine.start()

                        val buffer = ByteArray(1024*8*1024)
                        bytesRead = 0
                        var lastCheck = System.currentTimeMillis()
                        do {
                            val cnt = din.read(buffer)
                            if(cnt >= 0) {
                                bytesRead += cnt
                                //Write data to the internal buffer of the data line where it will be delivered to the speaker.
                                sourceDataLine.write(buffer, 0, cnt)

                                if(System.currentTimeMillis()-lastCheck > 250) { // every 1/4th of a second
                                    TinyJukebox.sendPlayerUpdateIfNecessary()
                                    lastCheck = System.currentTimeMillis()
                                }
                            }

                        } while(cnt != -1)
                        //Block and wait for internal buffer of the data line to empty.
                        sourceDataLine.close()
                        bytesRead = 0
/*                        val gainControl = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                        gainControl.value = -2.0f
                        playing = true
                        clip.addLineListener {
                            if(it.type == LineEvent.Type.STOP) {
                                playing = false
                                currentClip = null
                                currentMusic = null
                            }
                        }*/
                    }
                } catch (e: Exception) {
                    TinyJukebox.sendError(e)
                    e.printStackTrace()
                    state.setPlaying(null, 0, 0)
                }
            }


            Thread.sleep(200)
        }
    }
}