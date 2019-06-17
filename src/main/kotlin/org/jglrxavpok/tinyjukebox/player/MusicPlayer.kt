package org.jglrxavpok.tinyjukebox.player

import org.jglrxavpok.tinyjukebox.Music
import org.jglrxavpok.tinyjukebox.TinyJukebox
import java.io.BufferedInputStream
import java.io.InputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.DataLine

/**
 * Current state of the MusicPlayer thread
 */
class State(var currentMusic: Music? = null, var format: AudioFormat? = null) {
    companion object {
        val nullSource = object: MusicSource {
            override val location: String
                get() = ""

            override fun createStream() = object: InputStream() {
                override fun read() = -1
            }

            override fun computeDurationInMillis() = 1L

            override fun fetchName() = "\u231B Loading \u231B" // '\u231B' is the hourglass emoji

        }
        val specialLoadingMusic = Music(nullSource)

        val nullFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 1, 2, 4f, false)
    }

    fun isPlaying() = currentMusic != null

    fun setPlaying(
        music: Music?,
        format: AudioFormat?
    ) {
        this.currentMusic = music
        this.format = format
    }

    fun setLoadingState() {
        currentMusic = specialLoadingMusic
        format = nullFormat
    }
}

/**
 * Thread playing the music
 */
object MusicPlayer: Thread("Music Player") {

    /**
     * Number of bytes read in the current audio stream (used to compute current position)
     */
    var bytesRead: Long = 0
    var state = State()
    /**
     * Last time a player state was sent to the clients
     */
    private var lastUpdate = 0L
    /**
     * Has a skip been requested for the current music?
     */
    private var skipRequested = false

    override fun run() {
        while(!Thread.currentThread().isInterrupted) {
            val music = TinyJukebox.pollQueue()
            try {
                if(music != null) {
                    state.setLoadingState()
                    updateClients(force = true)
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
                    // change format to one that is playable
                    val din = AudioSystem.getAudioInputStream(decodedFormat, input)

                    val timeoutInput = TimeoutInputStream(din, 1000)

                    // audio output
                    val dataLineInfo = DataLine.Info(SourceDataLine::class.java, decodedFormat)
                    val sourceDataLine = AudioSystem.getLine(dataLineInfo) as SourceDataLine
                    sourceDataLine.open(decodedFormat)
                    sourceDataLine.start()

                    // read buffer
                    val buffer = ByteArray(1024*8)
                    bytesRead = 0

                    state.setPlaying(music, din.format)
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

                                updateClients() // send playing state to clients if necessary
                            }

                            if(skipRequested) {
                                skipRequested = false
                                break // breaks out of reading -> skips current music
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

                    state.setPlaying(null, null)
                    bytesRead = 0
                    updateClients()
                } else {
                    updateClients()
                }
            } catch (e: Exception) {
                TinyJukebox.sendError(e)
                e.printStackTrace()
                state.setPlaying(null, null)
            }


            Thread.sleep(200)
        }
    }

    /**
     * Send an update to clients about the current state of the player if the last call to this function was more than 250ms ago
     */
    private fun updateClients(force: Boolean = false) {
        if(force || System.currentTimeMillis()-lastUpdate > 250) { // every 1/4th of a second
            TinyJukebox.sendPlayerUpdateIfNecessary()
            lastUpdate = System.currentTimeMillis()
        }
    }

    fun skip() {
        skipRequested = true
    }
}