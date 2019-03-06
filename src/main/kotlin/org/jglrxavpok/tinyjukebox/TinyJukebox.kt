package org.jglrxavpok.tinyjukebox

import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.stream.Collectors
import kotlin.concurrent.read
import kotlin.concurrent.write

object TinyJukebox {

    private val queue: MutableList<Music> = mutableListOf()
    private val queueLocks = ReentrantReadWriteLock(true)

    fun addToQueue(music: Music) {
        performChangesToQueue {
            add(music)
        }
    }

    fun removeFromQueue(music: Music) {
        performChangesToQueue { removeIf { it == music } }
    }

    fun emptyQueue() {
        performChangesToQueue(MutableList<Music>::clear)
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
            else
                removeAt(0)
        }
    }

}