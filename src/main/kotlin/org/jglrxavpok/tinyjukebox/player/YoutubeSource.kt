package org.jglrxavpok.tinyjukebox.player

import org.jglrxavpok.tinyjukebox.PipingThread
import java.io.*
import java.nio.channels.Pipe

/**
 * Represents music streamed from YouTube using youtube-dl
 */
class YoutubeSource(val url: String): MusicSource {
    private var duration: Long = -1

    /**
     * Compute music duration in millis from youtube-dl info
     */
    override fun computeDurationInMillis(): Long {
        if(duration < 0) {
            val process = ProcessBuilder()
            val ytdl = process.command("youtube-dl", "--get-duration", url).start()
            val reader = BufferedReader(InputStreamReader(ytdl.inputStream))
            val result = reader.readLine()
            reader.close()
            println(">> "+result)
            if(result.contains(":")) {
                val parts = result.split(":")
                if(parts.size == 3) {
                    duration = parts[0].toLong()*1000*60*24/*hours*/ + parts[2].toLong()*1000 + parts[1].toLong()*1000*60 // <minutes>:<seconds>
                } else {
                    duration = parts[1].toLong()*1000 + parts[0].toLong()*1000*60 // <minutes>:<seconds>
                }
            } else {
                duration = result.toLong()*1000
            }
            return duration
        }
        return duration
    }

    /**
     * Creates an audio stream from YT
     */
    override fun createStream(): InputStream {
        println("Attempting to read music from YT url: $url")
        val process = ProcessBuilder()
        val ytdl = process.command("youtube-dl", "-o", "-", url)

        val tmp = File("./music/yt/tmp.txt")
        ytdl.redirectError(tmp)

        val ffmpeg = ProcessBuilder().command("ffmpeg", "-i", "-", "-f", "mp3", "-vn", "-")
        val ytdlProcess = ytdl.start()
        val ffmpegProcess = ffmpeg.start()
        // val vlcProcess = vlc.start()
        PipingThread(ytdlProcess, ffmpegProcess).start()
        //PipingThread(out, vlcProcess.outputStream).start()
        /*return object: InputStream() {
            override fun read(): Int {
                return -1
            }

        }*/
        return ffmpegProcess.inputStream
    }

    override fun fetchName(): String {
        val ytdl = ProcessBuilder().command("youtube-dl", "--get-title", url).start()
        val reader = BufferedReader(InputStreamReader(ytdl.inputStream))
        val result = reader.readLine()
        reader.close()
        return result
    }
}
