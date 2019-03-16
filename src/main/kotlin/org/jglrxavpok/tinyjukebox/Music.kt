package org.jglrxavpok.tinyjukebox

import org.jglrxavpok.tinyjukebox.player.MusicSource

data class Music(val name: String, val source: MusicSource) {
    constructor(source: MusicSource): this(source.fetchName(), source)
} // TODO: sender?