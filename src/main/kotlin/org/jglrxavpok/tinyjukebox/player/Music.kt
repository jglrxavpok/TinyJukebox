package org.jglrxavpok.tinyjukebox.player

import org.jglrxavpok.tinyjukebox.player.MusicSource

/**
 * Represents a music inside the queue or being played
 */
data class Music(val name: String, val source: MusicSource, val duration: Long) {
    constructor(source: MusicSource): this(source.fetchName(), source, source.computeDurationInMillis())
} // TODO: sender?
