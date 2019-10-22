package org.jglrxavpok.tinyjukebox

import org.jglrxavpok.tinyjukebox.player.MusicPlayer
import kotlin.system.exitProcess

/**
 * Handles commands received from the command line
 */
object Commands {

    fun execute(parts: List<String>) {
        if(parts.isEmpty())
            return
        val command = parts[0]
        when(command) {
            "monitor", "status" -> {
                displayStatus()
            }
            "skip" -> {
                MusicPlayer.skip()
            }
            "stop" -> {
                exitProcess(0)
            }
            else -> println("Invalid command ${parts.joinToString(" ")}")
        }
    }

    private fun displayStatus() {
        println("[==== TinyJukebox Status ====]")
        println("--- Music player ---")
        println("Current music: ${MusicPlayer.state.currentMusic?.name ?: "None"}")
        println("Music source: ${MusicPlayer.state.currentMusic?.source ?: "None"}")
        println("Duration in ms: ${MusicPlayer.state.currentMusic?.duration ?: "-1"}")
        println("Reading progress (ms): ${MusicPlayer.position()}")
        println("Reading progress (bytes): ${MusicPlayer.bytesRead}")
        println("Skip requested: ${MusicPlayer.isSkipRequested()}")
        println("Debug info: ${MusicPlayer.debugInfo}")

        println()
        println("--- Queue ---")
        TinyJukebox.performChangesToQueue {
            for((i, entry) in this.withIndex()) {
                print(" ${i+1}. ${entry.music.name} ")
                if(entry.locked) {
                    print("- Locked")
                }
                println()
            }
        }
        println("[============================]")
    }
}