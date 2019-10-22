package org.jglrxavpok.tinyjukebox.player

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
    var isLoading: Boolean = false

    companion object {
        val nullSource = object: MusicSource {
            override val location: String
                get() = ""

            override fun createStream() = object: InputStream() {
                override fun read() = -1
            }

            override fun computeDurationInMillis() = 1L

            override fun fetchName() = "__LOADING__"

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
        isLoading = false
    }

    fun setLoadingState() {
        currentMusic = specialLoadingMusic
        format = nullFormat
        isLoading = true
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

    internal var debugInfo = "unitialized"

    override fun run() {
        while(!Thread.currentThread().isInterrupted) {
            debugInfo = "polling queue"
            val musicEntry = TinyJukebox.pollQueue()
            try {
                if(musicEntry != null) {
                    debugInfo = "loading music"

                    val music = musicEntry.music
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

                    debugInfo = "opening audio output"

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
                            debugInfo = "reading from input"

                            cnt = timeoutInput.read(buffer)
                            if(cnt >= 0) {
                                bytesRead += cnt
                                debugInfo = "write to line"

                                writeToLine(sourceDataLine, buffer, cnt)

                                updateClients() // send playing state to clients if necessary
                            }

                            if(skipRequested) {
                                skipRequested = false
                                break // breaks out of reading -> skips current music
                            }
                        } catch (e: Exception) {
                            debugInfo = "cleaning up after exception"

                            timeoutInput.close()
                            e.printStackTrace()
                            break
                        }
                    } while(cnt != -1)
                    //Block and wait for internal buffer of the data line to empty.
                    sourceDataLine.drain()
                    sourceDataLine.stop()
                    sourceDataLine.close()
                    din.close()
                    debugInfo = "cleaning up"

                    state.setPlaying(null, null)
                    bytesRead = 0

                    debugInfo = "reset"
                    updateClients()
                } else {
                    updateClients()
                }
            } catch (e: Exception) {
                TinyJukebox.sendError(e)
                e.printStackTrace()
                state.setPlaying(null, null)
            }

            debugInfo = "polling queue"

            sleep(200)
        }
    }

    private fun writeToLine(sourceDataLine: SourceDataLine, buffer: ByteArray, cnt: Int) {
        if(sourceDataLine.available() == 0) {
            sourceDataLine.drain()
        }
        //Write data to the internal buffer of the data line where it will be delivered to the speaker.
        sourceDataLine.write(buffer, 0, cnt)

        debugInfo = "update clients"
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

    fun isSkipRequested() = skipRequested

    fun position(): Long {
        return if(state.isPlaying()) {
            val frame = bytesRead / state.format!!.frameSize
            (frame / state.format!!.frameRate * 1000.0).toLong() // in milliseconds
        } else -1
    }
}