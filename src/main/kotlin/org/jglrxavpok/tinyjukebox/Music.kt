package org.jglrxavpok.tinyjukebox

import org.jglrxavpok.tinyjukebox.player.MusicSource

/**
 * Represents a music inside the queue or being played
 */
data class Music(val name: String, val source: MusicSource, val duration: Long) {
    constructor(source: MusicSource): this(source.fetchName(), source, source.computeDurationInMillis())
} // TODO: sender?

/**
 * Converts a duration in milliseconds into a String in the format <minutes>:<seconds>
 */
fun Long.toMinutesAndSeconds(): String {
    val seconds = this / 1_000
    val minutes = seconds / 60
    return "$minutes:${String.format("%02d", seconds % 60)}"
}