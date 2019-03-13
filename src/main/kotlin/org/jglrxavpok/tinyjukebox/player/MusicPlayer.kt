package org.jglrxavpok.tinyjukebox.player

import org.jglrxavpok.tinyjukebox.Music
import org.jglrxavpok.tinyjukebox.TinyJukebox
import java.io.BufferedInputStream
import java.io.FileInputStream
import javax.sound.sampled.*
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.AudioFormat

object MusicPlayer: Thread("Music Player") {

    var playing = false
        private set
    var currentClip: Clip? = null
    var currentMusic: Music? = null

    override fun run() {
        while(!Thread.currentThread().isInterrupted) {
            if(!playing) {
                val music = TinyJukebox.pollQueue()
                try {
                    if(music != null) {
                        val clip = AudioSystem.getClip()
                        currentClip = clip
                        currentMusic = music
                        println(">> Playing ${music.name} / ${music.source}")
                        val input = AudioSystem.getAudioInputStream(BufferedInputStream(music.source.createStream()))
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
                        clip.open(din)
                        val gainControl = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                        gainControl.value = -2.0f
                        playing = true
                        clip.start()
                        clip.addLineListener {
                            if(it.type == LineEvent.Type.STOP) {
                                playing = false
                                currentClip = null
                                currentMusic = null
                            }
                        }
                    } else {
                        TinyJukebox.sendPlayerUpdateIfNecessary()
                    }
                } catch (e: Exception) {
                    TinyJukebox.sendError(e)
                    e.printStackTrace()
                    playing = false
                }
            } else {
                TinyJukebox.sendPlayerUpdateIfNecessary()
            }


            Thread.sleep(200)
        }
    }
}