package org.jglrxavpok.tinyjukebox

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.jglrxavpok.tinyjukebox.auth.AuthChecker
import org.jglrxavpok.tinyjukebox.auth.Permissions
import org.jglrxavpok.tinyjukebox.auth.Session
import org.jglrxavpok.tinyjukebox.auth.Session.Companion.login
import org.jglrxavpok.tinyjukebox.http.HttpInfo
import org.jglrxavpok.tinyjukebox.player.FileSource
import org.jglrxavpok.tinyjukebox.player.MusicPlayer
import org.jglrxavpok.tinyjukebox.player.YoutubeSource
import java.io.*
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.HashMap

/**
 * Object responsible to act when "/action/<some location>" is requested via a POST request
 */
object WebActions {

    /**
     * Regex Pattern used to extract information from the Youtube search results page
     */
    val pattern = Pattern.compile(
        "<span class=\"video-time\".+?(?=>)>(?<DURATION>.*?)<\\/span><\\/span><\\/div><\\/a>(.|\\n)+?(?=<\\/span><\\/li><\\/ul><\\/button>)<\\/span><\\/li><\\/ul><\\/button>(.|\\n)*?(?=<div class=\"yt-lockup-content\")<div class=\"yt-lockup-content\">.*?href=\"\\/watch\\?v=(?<ID>.*?)\".*?title=\"(?<NAME>.*?)\".*?<a href=\"\\/(user|channel)\\/.+?\" class=\"yt-uix-sessionlink.*?>(?<CHANNEL>.*?)<"
    )

    /**
     * An action
     */
    open class Action(val id: String,
                      val action: (HttpInfo) -> Unit,
                      vararg _requiredPermissions: Permissions
                      ) {
        val requiredPermissions = arrayListOf(*_requiredPermissions)
    }

    /**
     * List of all actions supported by TinyJukebox
     */
    val actionList = listOf(
        Action("upload", this::upload, Permissions.Upload), // upload a local or a youtube link
        Action("ytsearch", this::ytsearch, Permissions.Upload), // search a video on YT
        Action("auth", AuthChecker.checkAuth(null)), // check authenfication
        Action("playercontrol/empty", TinyJukebox::emptyQueue, Permissions.EmptyQueue), // empty the current queue, requires authentification
        Action("playercontrol/skip", this::skip, Permissions.Skip), // skips the current track, requires authentification
        Action("playercontrol/remove", this::removeFromQueue, Permissions.Remove), // remove the selected track, requires authentification
        Action("playercontrol/movetostart", this::moveToStart, Permissions.Move), // move the selected track at the head of the queue
        Action("playercontrol/movetoend", this::moveToEnd, Permissions.Move), // move the selected track at the bottom the queue
        Action("playercontrol/moveup", this::moveUp, Permissions.Move), // move the selected track up the queue
        Action("playercontrol/movedown", this::moveDown, Permissions.Move), // move the selected track up the queue

        Action("login", Session.Companion::login), // move the selected track up the queue
        Action("signup", Session.Companion::signup), // move the selected track up the queue
        Action("logout", Session.Companion::logout) // move the selected track up the queue
    )

    private fun skip(httpInfo: HttpInfo) {
        MusicPlayer.skip()
        MusicPlayer.state.currentMusic?.let {
            TJDatabase.onMusicSkip(it.name)
        }
    }

    /**
     * Remove tracks named as given by the client in 'clientReader'
     */
    private fun removeFromQueue(httpInfo: HttpInfo) {
        with(httpInfo) {
            val nameToRemove = URLDecoder.decode(clientReader.readLine(), "UTF-8")
            println("remove: $nameToRemove")
            val index = clientReader.readLine().toInt()
            if(!TinyJukebox.removeFromQueue(nameToRemove, index)) {
                writer.println("invalid position")
            } else {
                TJDatabase.onMusicSkip(nameToRemove)
            }
        }
    }

    /**
     * Moves a track named as given by the client in 'clientReader'
     */
    private fun moveToStart(httpInfo: HttpInfo) {
        with(httpInfo) {
            val nameToRemove = URLDecoder.decode(clientReader.readLine(), "UTF-8")
            val index = clientReader.readLine().toInt()
            if(!TinyJukebox.moveToStart(nameToRemove, index)) {
                writer.println("invalid position")
            }
        }
    }

    /**
     * Moves a track named as given by the client in 'clientReader'
     */
    private fun moveToEnd(httpInfo: HttpInfo) {
        with(httpInfo) {
            val nameToRemove = URLDecoder.decode(clientReader.readLine(), "UTF-8")
            val index = clientReader.readLine().toInt()
            if(!TinyJukebox.moveToEnd(nameToRemove, index)) {
                writer.println("invalid position")
            }
        }
    }

    /**
     * Moves a track named as given by the client in 'clientReader'
     */
    private fun moveUp(httpInfo: HttpInfo) {
        with(httpInfo) {
            val nameToRemove = URLDecoder.decode(clientReader.readLine(), "UTF-8")
            val index = clientReader.readLine().toInt()
            if(!TinyJukebox.moveUp(nameToRemove, index)) {
                writer.println("invalid position")
            }
        }
    }

    /**
     * Moves a track named as given by the client in 'clientReader'
     */
    private fun moveDown(httpInfo: HttpInfo) {
        with(httpInfo) {
            val nameToRemove = URLDecoder.decode(clientReader.readLine(), "UTF-8")
            val index = clientReader.readLine().toInt()
            if(!TinyJukebox.moveDown(nameToRemove, index)) {
                writer.println("invalid position")
            }
        }
    }

    /**
     * Searches Youtube for the query given by the client in 'clientReader'/'clientInput'
     */
    private fun ytsearch(httpInfo: HttpInfo) {
        with(httpInfo) {
            val query = clientReader.readLine()
            val queryURL = URL("https://www.youtube.com/results?search_query=${query.replace(" ", "+")}")
            val text = queryURL.readText(StandardCharsets.UTF_8)

            // for debug File("./tmp.txt").writeText(text)
            val interestingPart = text.substring(text.indexOf("<span class=\"video-time\"") .. text.lastIndexOf("</div><div class=\"yt-lockup-meta \""))
            // for debug  File("./tmp2.txt").writeText(interestingPart)
            writer.println(createAnswerJson(interestingPart))
        }
    }

    /**
     * Generates the JSON sent to the client that requested a Youtube search
     * Contains the name, video id, channel and duration of results
     */
    private fun createAnswerJson(text: String): JsonArray {
        val array = JsonArray()
        val parts = text.split("/a></div><div class=\"yt-lockup-meta \"")
        for(p in parts) {
            val matcher = pattern.matcher(p)
            while(matcher.find()) {
                val title = matcher.group("NAME").toByteArray().toString(Charsets.UTF_8)
                val id = matcher.group("ID").toByteArray().toString(Charsets.UTF_8)
                val channel = matcher.group("CHANNEL").toByteArray().toString(Charsets.UTF_8)
                val duration = matcher.group("DURATION").toByteArray().toString(Charsets.UTF_8)

                val videoObj = JsonObject()
                videoObj.addProperty("title", title)
                videoObj.addProperty("id", id)
                videoObj.addProperty("channel", channel)
                videoObj.addProperty("duration", duration)

                array.add(videoObj)
            }
        }
        return array
    }

    private fun upload(httpInfo: HttpInfo) {
        with(httpInfo) {
            val fileSource = attributes["File-Source"]
            val music: Music? = when(fileSource) {
                "Local" -> uploadLocal(clientReader, attributes)
                "Youtube" -> uploadYoutube(clientReader, attributes)
                else -> null
            }
            music?.let {
                TJDatabase.onMusicUpload(session, music)
                TinyJukebox.addToQueue(it)
            }
        }
    }

    /**
     * Download a file sent by the client.
     * The file is encoded in Base64 and sent via 'clientReader'
     */
    private fun uploadLocal(clientReader: BufferedReader, attributes: Map<String, String>): Music? {
        val filename = attributes["File-Name"]
        if(filename == null) {
            TinyJukebox.sendError(IllegalArgumentException("No file name ?! Are you trying to break me ?! >:("))
            return null
        }

        // verifies that the client is not trying to access invalid files (eg by trying to access files in '../')
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

        // saves the downloaded file
        val target = BufferedOutputStream(FileOutputStream(file))
        val line = clientReader.readLine()
        val input = Base64.getMimeDecoder().decode(line.substringAfter(";base64,"))
        target.write(input)
        target.flush()
        target.close()

        val source = FileSource(file)
        return Music(file.nameWithoutExtension, source, source.computeDurationInMillis())
    }

    private fun uploadYoutube(clientReader: BufferedReader, attributes: Map<String, String>): Music? {
        // simply create the source from the given url
        val url = clientReader.readLine()
        if(url.isBlank()) {
            return null
        }
        return Music(YoutubeSource(url))
    }

    /**
     * Perform an action from 'actionList' based on 'actionType' and the data the client is sending
     */
    fun perform(httpInfo: HttpInfo, actionType: String) {
        try {
            with(httpInfo) {
                val action = actionList.first { it.id.toLowerCase() == actionType.toLowerCase()}
                session.checkPermissions(action.requiredPermissions)
                action.action(httpInfo)
            }
        } catch (e: Exception) {
            TinyJukebox.sendError(IllegalArgumentException("Failed to perform action: $e"))
            e.printStackTrace()
        }
    }

    /**
     * Checks if a given action type exists
     */
    fun isValidAction(actionType: String): Boolean {
        return actionList.any { it.id.toLowerCase() == actionType.toLowerCase() }
    }

}