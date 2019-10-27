package org.jglrxavpok.tinyjukebox.http

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.jglrxavpok.tinyjukebox.player.Music
import org.jglrxavpok.tinyjukebox.TinyJukebox
import org.jglrxavpok.tinyjukebox.auth.AuthChecker
import org.jglrxavpok.tinyjukebox.auth.Permissions
import org.jglrxavpok.tinyjukebox.auth.Session
import org.jglrxavpok.tinyjukebox.player.FileSource
import org.jglrxavpok.tinyjukebox.player.MusicPlayer
import org.jglrxavpok.tinyjukebox.player.YoutubeSource
import org.jglrxavpok.tinyjukebox.templating.TJDatabase
import java.io.*
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.*
import java.util.regex.Pattern

/**
 * Object responsible to act when "/action/<some location>" is requested via a POST request
 */
// TODO: Move everything to different controllers
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
        Action("auth", AuthChecker.checkAuth(null)), // check authenfication
        Action(
            "playercontrol/empty",
            TinyJukebox::emptyQueue,
            Permissions.EmptyQueue
        ), // empty the current queue, requires authentification
        Action("playercontrol/skip", this::skip, Permissions.Skip), // skips the current track, requires authentification
        Action(
            "playercontrol/remove",
            this::removeFromQueue
        ), // remove the selected track, requires authentification
        Action(
            "playercontrol/movetostart",
            this::moveToStart,
            Permissions.Move
        ), // move the selected track at the head of the queue
        Action("playercontrol/movetoend", this::moveToEnd, Permissions.Move), // move the selected track at the bottom the queue
        Action("playercontrol/moveup", this::moveUp, Permissions.Move), // move the selected track up the queue
        Action("playercontrol/movedown", this::moveDown, Permissions.Move), // move the selected track up the queue
        Action("playercontrol/lock", this::lock, Permissions.Lock), // move the selected track up the queue
        Action("playercontrol/unlock", this::unlock, Permissions.Lock), // move the selected track up the queue

        Action("login", Session.Companion::login), // move the selected track up the queue
        Action("signup", Session.Companion::signup), // move the selected track up the queue
        Action(
            "logout",
            Session.Companion::logout
        ) // move the selected track up the queue
    )

    private fun skip(httpInfo: HttpInfo) {
        MusicPlayer.skip()
        MusicPlayer.state.currentMusic?.let {
            TJDatabase.onMusicSkip(it.name)
        }
    }

    private fun unlock(httpInfo: HttpInfo) {
        with(httpInfo) {
            val nameToRemove = URLDecoder.decode(clientReader.readLine(), "UTF-8")
            val index = clientReader.readLine().toInt()
            val entry = TinyJukebox.getFromQueue(nameToRemove, index)
            entry?.locked = false
            TinyJukebox.sendQueueUpdate()
        }
    }

    private fun lock(httpInfo: HttpInfo) {
        with(httpInfo) {
            val nameToRemove = URLDecoder.decode(clientReader.readLine(), "UTF-8")
            val index = clientReader.readLine().toInt()
            val entry = TinyJukebox.getFromQueue(nameToRemove, index)
            entry?.locked = true
            TinyJukebox.sendQueueUpdate()
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
            val entry = TinyJukebox.getFromQueue(nameToRemove, index)
            if(entry == null) {
                writer.println("invalid position")
                return
            }
            if(entry.locked) {
                session.checkPermissions(arrayListOf(Permissions.RemoveLocked))
            } else {
                session.checkPermissions(arrayListOf(Permissions.Remove))
            }
            if(!TinyJukebox.removeFromQueue(nameToRemove, index)) {
                writer.println("invalid position")
            } else {
                TJDatabase.onMusicSkip(nameToRemove)
            }
        }
    }

    private fun verifyLocks(nameToRemove: String, index: Int, replacedIndex: Int, session: Session, writer: PrintWriter): Boolean {
        val entry = TinyJukebox.getFromQueue(nameToRemove, index)
        if(entry == null) {
            writer.println("invalid position, no corresponding entry")
            return false
        }
        if(entry.locked) {
            session.checkPermissions(arrayListOf(Permissions.MoveLocked))
        } else {
            session.checkPermissions(arrayListOf(Permissions.Move))
        }
        val movedEntry = TinyJukebox.performChangesToQueue {
            if(replacedIndex in this.indices) {
                this[replacedIndex]
            } else {
                null
            }
        }
        if(movedEntry == null) {
            writer.println("invalid position")
            return false
        }
        if(movedEntry.locked) {
            session.checkPermissions(arrayListOf(Permissions.MoveLocked))
        } else {
            session.checkPermissions(arrayListOf(Permissions.Move))
        }
        return true
    }

    /**
     * Moves a track named as given by the client in 'clientReader'
     */
    private fun moveToStart(httpInfo: HttpInfo) {
        with(httpInfo) {
            val nameToRemove = URLDecoder.decode(clientReader.readLine(), "UTF-8")
            val index = clientReader.readLine().toInt()

            if(verifyLocks(nameToRemove, index, 0, session, writer)) {
                if(!TinyJukebox.moveToStart(nameToRemove, index)) {
                    writer.println("invalid position")
                }
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
            val entry = TinyJukebox.getFromQueue(nameToRemove, index)
            if(entry == null) {
                writer.println("invalid position")
                return
            }
            if(entry.locked) {
                session.checkPermissions(arrayListOf(Permissions.MoveLocked))
            } else {
                session.checkPermissions(arrayListOf(Permissions.Move))
            }
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
            if(verifyLocks(nameToRemove, index, index-1, session, writer)) {
                if(!TinyJukebox.moveUp(nameToRemove, index)) {
                    writer.println("invalid position")
                }
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
            if(verifyLocks(nameToRemove, index, index+1, session, writer)) {
                if (!TinyJukebox.moveDown(nameToRemove, index)) {
                    writer.println("invalid position")
                }
            }
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
        return Music(
            file.nameWithoutExtension,
            source,
            source.computeDurationInMillis()
        )
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