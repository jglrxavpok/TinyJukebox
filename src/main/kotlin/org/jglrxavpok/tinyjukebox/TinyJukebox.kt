package org.jglrxavpok.tinyjukebox

import org.jglrxavpok.tinyjukebox.http.HttpInfo
import org.jglrxavpok.tinyjukebox.player.Music
import org.jglrxavpok.tinyjukebox.player.MusicEntry
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
    private val queue: MutableList<MusicEntry> = mutableListOf()
    private val queueLocks = ReentrantReadWriteLock(true)

    // previous state of the music player, used by sendPlayerUpdateIfNecessary
    private var currentlyPlaying: Boolean = false
    private var currentMusic: Music? = null
    private var currentPosition: Long = -1

    /**
     * Adds a music to the queue and send a update to clients
     */
    fun addToQueue(music: Music, uploader: String) {
        performChangesToQueue {
            add(MusicEntry(music, false, uploader))
            sendQueueUpdate()
        }
    }

    /**
     * Removes a non-locked music to the queue and send a update to clients
     */
    fun removeFromQueue(music: Music) {
        performChangesToQueue {
            removeIf { !it.locked && it.music == music }
            sendQueueUpdate()
        }
    }

    /**
     * Empty the queue and send a update to clients
     */
    fun emptyQueue(httpInfo: HttpInfo) {
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
    fun <T> performChangesToQueue(action: MutableList<MusicEntry>.() -> T): T {
        return queueLocks.write {
            queue.action()
        }
    }

    /**
     * Creates a fresh copy of the current queue (with synchronisation)
     */
    fun createCopyOfQueue(): MutableList<MusicEntry> {
        return queueLocks.read { queue.stream().collect(Collectors.toList()) }
    }

    /**
     * Gives the music at the head of the queue, or null if the queue is empty.
     * Takes care of synchronisation
     */
    fun pollQueue(): MusicEntry? {
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

        val position = MusicPlayer.position()
        // copy state
        currentMusic = playerState.currentMusic
        currentlyPlaying = currentMusic != null
        currentPosition = position ?: -1

        // send update if necessary
        if(hasUpdated) {
            if(currentlyPlaying) {
                val duration = currentMusic!!.duration
                val percent = position!!.toDouble()/duration.toDouble()
                websocket.sendPlayerUpdate(true, playerState.currentMusic!!.name, position, duration, percent, playerState.isLoading)
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

    fun getFromQueue(nameToRemove: String, index: Int): MusicEntry? {
        if(index < queue.size) {
            val foundName = queue[index].music.name
            if (foundName == nameToRemove) {
                return queue[index]
            }
        }
        return null
    }

    /**
     * Remove a given track
     */
    fun removeFromQueue(nameToRemove: String, index: Int): Boolean {
        val result = performChangesToQueue {
            if(index < size) {
                val foundName = this[index].music.name
                if(foundName == nameToRemove) {
                    this.removeAt(index)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        if(result)
            sendQueueUpdate()
        return result
    }

    /**
     * Move a given track up the queue
     */
    fun moveUp(nameToRemove: String, index: Int): Boolean {
        val result = performChangesToQueue {
            if(index in 1 until size) {
                val foundName = this[index].music.name
                if(foundName == nameToRemove) {
                    val music = this.removeAt(index)
                    this.add(index-1, music)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        if(result)
            sendQueueUpdate()
        return result
    }

    /**
     * Move a given track up the queue
     */
    fun moveToStart(nameToRemove: String, index: Int): Boolean {
        val result = performChangesToQueue {
            if(index in 1 until size) {
                val foundName = this[index].music.name
                if(foundName == nameToRemove) {
                    val music = this.removeAt(index)
                    this.add(0, music)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        if(result)
            sendQueueUpdate()
        return result
    }

    /**
     * Move a given track up the queue
     */
    fun moveToEnd(nameToRemove: String, index: Int): Boolean {
        val result = performChangesToQueue {
            if(index in 0 until size) {
                val foundName = this[index].music.name
                if(foundName == nameToRemove) {
                    val music = this.removeAt(index)
                    this.add(music)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        if(result)
            sendQueueUpdate()
        return result
    }

    /**
    * Move a given track up the queue
    */
    fun moveDown(nameToRemove: String, index: Int): Boolean {
        val result = performChangesToQueue {
            if(index in 0 until size-1) {
                val foundName = this[index].music.name
                if(foundName == nameToRemove) {
                    val music = this.removeAt(index)
                    this.add(index+1, music)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        if(result) {
            sendQueueUpdate()
        }
        return result
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