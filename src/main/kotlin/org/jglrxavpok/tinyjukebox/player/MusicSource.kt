package org.jglrxavpok.tinyjukebox.player

import java.io.InputStream

/**
 * Represents a generic music source that can be played
 */
interface MusicSource {

    val location: String

    /**
     * Create a new audio stream for this source
     */
    fun createStream(): InputStream

    /**
     * Computes the length of the music in milliseconds
     */
    fun computeDurationInMillis(): Long

    /**
     * Gets the title of the music
     */
    fun fetchName(): String
}