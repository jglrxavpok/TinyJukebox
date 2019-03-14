package org.jglrxavpok.tinyjukebox

import org.jglrxavpok.tinyjukebox.player.MusicPlayer
import org.jglrxavpok.tinyjukebox.websocket.JukeboxWebsocketServer
import java.lang.Exception
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.stream.Collectors
import javax.sound.sampled.AudioInputStream
import kotlin.concurrent.read
import kotlin.concurrent.write
import org.tritonus.share.sampled.AudioUtils.getFrameSize



object TinyJukebox {

    private lateinit var websocket: JukeboxWebsocketServer
    private val queue: MutableList<Music> = mutableListOf()
    private val queueLocks = ReentrantReadWriteLock(true)

    // previous state of the music player, used by sendPlayerUpdateIfNecessary
    private var currentlyPlaying: Boolean = false
    private var currentMusic: Music? = null
    private var currentPosition: String = "-1:-1"

    fun addToQueue(music: Music) {
        performChangesToQueue {
            add(music)
            sendUpdate()
        }
    }

    fun removeFromQueue(music: Music) {
        performChangesToQueue {
            removeIf { it == music }
            sendUpdate()
        }
    }

    fun emptyQueue() {
        performChangesToQueue {
            clear()
            sendUpdate()
        }
    }

    fun sendUpdate() {
        if(this::websocket.isInitialized)
            websocket.sendQueueToEveryone()
    }

    fun <T> performChangesToQueue(action: MutableList<Music>.() -> T): T {
        return queueLocks.write {
            queue.action()
        }
    }

    fun createCopyOfQueue(): MutableList<Music> {
        return queueLocks.read { queue.stream().collect(Collectors.toList()) }
    }

    fun pollQueue(): Music? {
        return performChangesToQueue {
            if(isEmpty())
                null
            else {
                val result = removeAt(0)
                sendUpdate()
                result
            }
        }
    }

    fun setWebsocket(websocket: JukeboxWebsocketServer) {
        this.websocket = websocket
    }

    fun sendPlayerUpdateIfNecessary() {
        val playerState = MusicPlayer.state
        val position = System.currentTimeMillis()-playerState.startTime
        val hasUpdated =    /*if(!currentlyPlaying) {
                                audioStream != null // started playing
                            } else {
                                if(audioStream == null) {
                                    true // stopped playing
                                }
                                else {
                                    val music = MusicPlayer.currentMusic
                                    if(music != currentMusic) {
                                        true // music changed
                                    } else {
                                        currentPosition != position!!.toMinutesAndSeconds()
                                    }
                                }
                            }*/true

        // copy state
        currentMusic = playerState.currentMusic
        currentlyPlaying = currentMusic != null
        currentPosition = position.toMinutesAndSeconds()

        // send update if necessary
        if(hasUpdated) {
            if(currentlyPlaying) {
                val duration = playerState.duration
                val percent = position.toDouble()/duration.toDouble()
                websocket.sendPlayerUpdate(true, playerState.currentMusic!!.name, position.toMinutesAndSeconds(), duration.toMinutesAndSeconds(), percent)
            } else {
                // not playing anything
                websocket.sendPlayerUpdate(false)
            }
        }
    }

    private fun Long.toMinutesAndSeconds(): String {
        val seconds = this / 1_000
        val minutes = seconds / 60
        return "$minutes:${String.format("%02d", seconds % 60)}"
    }

    fun sendError(error: Exception) {
        val actualErrorMessage = error.javaClass.canonicalName+": "+error.message
        val errorMessage = "error\n$actualErrorMessage"
        websocket.broadcast(errorMessage)
    }

}