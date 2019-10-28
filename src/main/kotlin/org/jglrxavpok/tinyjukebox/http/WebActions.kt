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