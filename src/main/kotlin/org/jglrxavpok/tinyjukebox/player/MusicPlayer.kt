package org.jglrxavpok.tinyjukebox.player

import org.jglrxavpok.tinyjukebox.Music
import org.jglrxavpok.tinyjukebox.TinyJukebox
import java.io.BufferedInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.DataLine

class State(var currentMusic: Music? = null, var duration: Long = 1L, var format: AudioFormat? = null) {
    fun isPlaying() = currentMusic != null

    fun setPlaying(
        music: Music?,
        duration: Long,
        format: AudioFormat?
    ) {
        this.currentMusic = music
        this.duration = duration
        this.format = format
    }
}

object MusicPlayer: Thread("Music Player") {

    var bytesRead: Long = 0
    var state = State()
    private var lastCheck = 0L
    private var skipRequested = false

    override fun run() {
        while(!Thread.currentThread().isInterrupted) {
            val music = TinyJukebox.pollQueue()
            try {
                if(music != null) {
                    val unbufferedStream = music.source.createStream()
                    val sourceStream = BufferedInputStream(unbufferedStream)
                    val input = AudioSystem.getAudioInputStream(sourceStream)
                    val baseFormat = input.format
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

                    val timeoutInput = TimeoutInputStream(din, 1000)

                    val dataLineInfo = DataLine.Info(SourceDataLine::class.java, decodedFormat)
                    val sourceDataLine = AudioSystem.getLine(dataLineInfo) as SourceDataLine
                    sourceDataLine.open(decodedFormat)
                    sourceDataLine.start()

                    val buffer = ByteArray(1024*8*1024)
                    bytesRead = 0

                    state.setPlaying(music, music.source.computeDurationInMillis(), din.format)
                    println(">> Playing ${music.name} / ${music.source}")
                    println(">> Format is ${din.format} ${din.format.frameRate} - ${din.format.frameSize}")

                    do {
                        val cnt: Int
                        try {
                            cnt = timeoutInput.read(buffer)
                            if(cnt >= 0) {
                                bytesRead += cnt
                                //Write data to the internal buffer of the data line where it will be delivered to the speaker.
                                sourceDataLine.write(buffer, 0, cnt)

                                updateClients()
                            }

                            if(skipRequested) {
                                skipRequested = false
                                break
                            }
                        } catch (e: Exception) {
                            timeoutInput.close()
                            e.printStackTrace()
                            break
                        }
                    } while(cnt != -1)
                    //Block and wait for internal buffer of the data line to empty.
                    sourceDataLine.flush()
                    sourceDataLine.close()
                    state.setPlaying(null, 0, null)
                    bytesRead = 0
                    updateClients()
                } else {
                    updateClients()
                }
            } catch (e: Exception) {
                TinyJukebox.sendError(e)
                e.printStackTrace()
                state.setPlaying(null, 0, null)
            }


            Thread.sleep(200)
        }
    }

    /**
     * Send an update to clients about the current state of the player if the last call to this function was more than 250ms ago
     */
    private fun updateClients() {
        if(System.currentTimeMillis()-lastCheck > 250) { // every 1/4th of a second
            TinyJukebox.sendPlayerUpdateIfNecessary()
            lastCheck = System.currentTimeMillis()
        }
    }

    fun skip() {
        skipRequested = true
    }
}