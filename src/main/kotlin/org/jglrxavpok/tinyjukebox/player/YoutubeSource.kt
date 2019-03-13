package org.jglrxavpok.tinyjukebox.player

import org.jglrxavpok.tinyjukebox.PipingThread
import java.io.InputStream
import java.io.PipedOutputStream

class YoutubeSource(val url: String): MusicSource {
    override fun createStream(): InputStream {
        println("Attempting to read music from YT url: $url")
        val process = ProcessBuilder()
        val ytdl = process.command("youtube-dl", "-o", "-", url)
        val ffmpeg = ProcessBuilder().command("ffmpeg", "-i", "-", "-f", "mp3", "-vn", "-")
        val ytdlProcess = ytdl.start()
        val ffmpegProcess = ffmpeg.start()
        PipingThread(ytdlProcess, ffmpegProcess).start()
        PipingThread(ytdlProcess, ffmpegProcess).start()
        return ffmpegProcess.inputStream
    }
}