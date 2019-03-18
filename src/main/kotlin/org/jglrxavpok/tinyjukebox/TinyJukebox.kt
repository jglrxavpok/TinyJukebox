package org.jglrxavpok.tinyjukebox

import org.jglrxavpok.tinyjukebox.player.MusicPlayer
import org.jglrxavpok.tinyjukebox.websocket.JukeboxWebsocketServer
import java.lang.Exception
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.stream.Collectors
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Object responsible to hold the music queue and perform actions on it.
 * Also sends update messages through websockets
 */
object TinyJukebox {

    private lateinit var websocket: JukeboxWebsocketServer
    private val queue: MutableList<Music> = mutableListOf()
    private val queueLocks = ReentrantReadWriteLock(true)

    // previous state of the music player, used by sendPlayerUpdateIfNecessary
    private var currentlyPlaying: Boolean = false
    private var currentMusic: Music? = null
    private var currentPosition: String = "-1:-1"

    /**
     * Adds a music to the queue and send a update to clients
     */
    fun addToQueue(music: Music) {
        performChangesToQueue {
            add(music)
            sendQueueUpdate()
        }
    }

    /**
     * Removes a music to the queue and send a update to clients
     */
    fun removeFromQueue(music: Music) {
        performChangesToQueue {
            removeIf { it == music }
            sendQueueUpdate()
        }
    }

    /**
     * Empty the queue and send a update to clients
     */
    fun emptyQueue() {
        performChangesToQueue {
            clear()
            sendQueueUpdate()
        }
    }

    /**
     * Send a queue update to clients
     */
    fun sendQueueUpdate() {
        if(this::websocket.isInitialized)
            websocket.sendQueueToEveryone()
    }

    /**
     * Modifies the queue with the given action. Takes care of synchronisation
     */
    fun <T> performChangesToQueue(action: MutableList<Music>.() -> T): T {
        return queueLocks.write {
            queue.action()
        }
    }

    /**
     * Creates a fresh copy of the current queue (with synchronisation)
     */
    fun createCopyOfQueue(): MutableList<Music> {
        return queueLocks.read { queue.stream().collect(Collectors.toList()) }
    }

    /**
     * Gives the music at the head of the queue, or null if the queue is empty.
     * Takes care of synchronisation
     */
    fun pollQueue(): Music? {
        return performChangesToQueue {
            if(isEmpty())
                null
            else {
                val result = removeAt(0)
                sendQueueUpdate()
                result
            }
        }
    }

    fun setWebsocket(websocket: JukeboxWebsocketServer) {
        this.websocket = websocket
    }

    /**
     * Send a player state (music name, position, duration) update to clients
     */
    fun sendPlayerUpdateIfNecessary() {
        if(!this::websocket.isInitialized)
            return
        val playerState = MusicPlayer.state
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

        val position = if(playerState.isPlaying()) {
            val frame = MusicPlayer.bytesRead / playerState.format!!.frameSize
            (frame / playerState.format!!.frameRate * 1000.0).toLong() // in milliseconds
        } else null
        // copy state
        currentMusic = playerState.currentMusic
        currentlyPlaying = currentMusic != null
        currentPosition = position?.toMinutesAndSeconds() ?: ""

        // send update if necessary
        if(hasUpdated) {
            if(currentlyPlaying) {
                val duration = currentMusic!!.duration
                val percent = position!!.toDouble()/duration.toDouble()
                websocket.sendPlayerUpdate(true, playerState.currentMusic!!.name, position.toMinutesAndSeconds(), duration.toMinutesAndSeconds(), percent)
            } else {
                // not playing anything
                websocket.sendPlayerUpdate(false)
            }
        }
    }

    /**
     * Send a given error to all clients
     */
    fun sendError(error: Exception) {
        val actualErrorMessage = error.javaClass.canonicalName+": "+error.message
        val errorMessage = "error\n$actualErrorMessage"
        error.printStackTrace()
        if(this::websocket.isInitialized)
            websocket.broadcast(errorMessage)
    }

    /**
     * Remove all tracks with the given name
     */
    fun removeFromQueue(nameToRemove: String) {
        println("Remove: '$nameToRemove'")
        performChangesToQueue {
            this.removeIf { it.name == nameToRemove }
            sendQueueUpdate()
        }
    }

    /**
     * Updates the quote for all clients
     */
    fun sendQuote(currentQuote: String) {
        if(this::websocket.isInitialized) {
            websocket.broadcast("quote\n$currentQuote")
        }
    }

}