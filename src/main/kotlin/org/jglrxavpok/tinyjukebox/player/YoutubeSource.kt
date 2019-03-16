package org.jglrxavpok.tinyjukebox.player

import org.jglrxavpok.tinyjukebox.PipingThread
import java.io.*
import java.nio.channels.Pipe

class YoutubeSource(val url: String): MusicSource {
    private var duration: Long = -1

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
                duration = parts[1].toLong()*1000 + parts[0].toLong()*1000*60 // <minutes>:<seconds>
            } else {
                duration = result.toLong()*1000
            }
            return duration
        }
        return duration
    }

    override fun createStream(): InputStream {
        println("Attempting to read music from YT url: $url")
        val process = ProcessBuilder()
        val ytdl = process.command("youtube-dl", "-o", "-", url)

        val tmp = File("./music/yt/tmp.txt")
        ytdl.redirectError(tmp)

        val ffmpeg = ProcessBuilder().command("ffmpeg", "-i", "-", "-f", "mp3", "-vn", "-")
        val vlc = ProcessBuilder().command("vlc", "-")
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
