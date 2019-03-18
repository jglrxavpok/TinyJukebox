package org.jglrxavpok.tinyjukebox

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.jglrxavpok.tinyjukebox.auth.AuthChecker
import org.jglrxavpok.tinyjukebox.player.FileSource
import org.jglrxavpok.tinyjukebox.player.MusicPlayer
import org.jglrxavpok.tinyjukebox.player.YoutubeSource
import java.io.*
import java.lang.Exception
import java.net.URL
import java.nio.file.Paths
import java.util.*
import java.util.regex.Pattern

object WebActions {

    val gson = Gson()
    //val pattern = Pattern.compile("<div class=\"yt-lockup-content\">.*?title=\"(?<NAME>.*?)\".*?</div></div></div></li>")
    val pattern = Pattern.compile("<div class=\"yt-lockup-content\">.*?href=\"/watch\\?v=(?<ID>.*?)\".*?title=\"(?<NAME>.*?)\".*?<a href=\"/(user|channel)/.*?\" class=\"yt-uix-sessionlink.*?>(?<CHANNEL>.*?)<.*?</div></div></div></li>")
    //val pattern = Pattern.compile("<div class=\"yt-lockup-content\">.*?title=\"(?<NAME>.*?)\".*?</div></div></div></li>")

    open class Action(val id: String, val action: (PrintWriter, Long, BufferedReader, InputStream, Map<String, String>) -> Unit)

    val id2actionMap = listOf(
        Action("upload", this::upload),
        Action("ytsearch", this::ytsearch),
        Action("auth", AuthChecker.checkAuth(null)),
        Action("playercontrol/empty", AuthChecker.checkAuth { writer, reader -> TinyJukebox.emptyQueue()}),
        Action("playercontrol/skip", AuthChecker.checkAuth { writer, reader -> MusicPlayer.skip()}),
        Action("playercontrol/remove", AuthChecker.checkAuth(this::removeFromQueue))
    )

    private fun removeFromQueue(writer: PrintWriter, clientReader: BufferedReader) {
        val nameToRemove = clientReader.readLine()
        TinyJukebox.removeFromQueue(nameToRemove)
    }

    private fun ytsearch(writer: PrintWriter, length: Long, clientReader: BufferedReader, clientInput: InputStream, attributes: Map<String, String>) {
        val query = clientReader.readLine()
        val queryURL = URL("https://www.youtube.com/results?search_query=${query.replace(" ", "+")}")
        val text = queryURL.readText()
        writer.println(createAnswerJson(text))
    }

    private fun createAnswerJson(text: String): JsonArray {
        val array = JsonArray()
        val matcher = pattern.matcher(text)
        while(matcher.find()) {
            val title = matcher.group("NAME")
            val id = matcher.group("ID")
            val channel = matcher.group("CHANNEL")

            val videoObj = JsonObject()
            videoObj.addProperty("title", title)
            videoObj.addProperty("id", id)
            videoObj.addProperty("channel", channel)
            array.add(videoObj)
        }
        return array
    }

    private fun upload(writer: PrintWriter, length: Long, clientReader: BufferedReader, clientInput: InputStream, attributes: Map<String, String>) {
        val fileSource = attributes["File-Source"]
        val music: Music? = when(fileSource) {
            "Local" -> uploadLocal(clientReader, attributes)
            "Youtube" -> uploadYoutube(clientReader, attributes)
            else -> null
        }
        music?.let {
            TinyJukebox.addToQueue(it)
        }
    }

    private fun uploadLocal(clientReader: BufferedReader, attributes: Map<String, String>): Music? {
        val filename = attributes["File-Name"]
        if(filename == null) {
            TinyJukebox.sendError(IllegalArgumentException("No file name ?! Are you trying to break me ?! >:("))
            return null
        }
        val root = Paths.get("music/").toAbsolutePath()
        val localFilePath = Paths.get("music/$filename").toAbsolutePath()
        val f = localFilePath.toFile()
        if(!f.canonicalPath.startsWith(root.toFile().canonicalPath)) {
            println("[ERROR uploadLocal] Tried to play invalid file at $localFilePath")
            TinyJukebox.sendError(IllegalArgumentException("Invalid file location, are you trying to break me ? :("))
            return null
        }
        val file = File("./music/$filename")
        if(file.isDirectory) {
            TinyJukebox.sendError(IllegalArgumentException("Invalid file location, that's a directory"))
            return null
        }
        if(!file.parentFile.exists()) {
            file.parentFile.mkdirs() // TODO check error
        }
        val target = BufferedOutputStream(FileOutputStream(file))

        val line = clientReader.readLine()
        val input = Base64.getMimeDecoder().decode(line.substringAfter(";base64,"))
        target.write(input)
        target.flush()
        target.close()

        println("file size=${file.length()}")
        val source = FileSource(file)
        return Music(file.nameWithoutExtension, source, source.computeDurationInMillis())
    }

    private fun uploadYoutube(clientReader: BufferedReader, attributes: Map<String, String>): Music? {
        val url = clientReader.readLine()
        return Music(YoutubeSource(url))
    }

    private fun extractDestination(logFile: String): String {
        val prefix = "[download] Destination: "
        return logFile.lines().first {
            it.startsWith(prefix)
        }.substring(prefix.length).substringBeforeLast(".")+".mp3"
    }

    fun perform(writer: PrintWriter, actionType: String, length: Long, reader: BufferedReader, clientInput: InputStream, attributes: Map<String, String>) {
        try {
            id2actionMap.first { it.id.toLowerCase() == actionType.toLowerCase()}.action(writer, length, reader, clientInput, attributes)
        } catch (e: Exception) {
            TinyJukebox.sendError(IllegalArgumentException("Failed to perform action: $e"))
            e.printStackTrace()
        }
    }

    fun isValidAction(actionType: String): Boolean {
        return id2actionMap.any { it.id.toLowerCase() == actionType.toLowerCase() }
    }

}