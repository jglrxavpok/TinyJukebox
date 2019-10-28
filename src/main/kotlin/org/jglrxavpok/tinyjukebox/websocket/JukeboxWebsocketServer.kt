package org.jglrxavpok.tinyjukebox.websocket

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.jglrxavpok.tinyjukebox.TinyJukebox
import org.jglrxavpok.tinyjukebox.auth.RSAPublicKey
import org.jglrxavpok.tinyjukebox.auth.Session
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Exception
import java.lang.StringBuilder
import java.net.InetSocketAddress
import java.util.*
import java.util.Base64.getEncoder
import kotlin.concurrent.thread

/**
 * Represents the WebSocket server
 */
class JukeboxWebsocketServer(address: InetSocketAddress): WebSocketServer(address) {

    private val clientsConnected = hashSetOf<String>()

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        conn?.send("Welcome!")
        val encodedPublicKey = RSAPublicKey.encoded
        val publicKey64 = Base64.getEncoder().encodeToString(encodedPublicKey)
        // it says PRIVATE but the public one is sent
        conn?.send("PublicKey\n-----BEGIN RSA PRIVATE KEY-----\n$publicKey64\n-----END RSA PRIVATE KEY-----")
        sendQueue(conn)
        println("client arrived!")
    }

    /**
     * Send the contents of the queue to the given client
     */
    private fun sendQueue(conn: WebSocket?) {
        conn?.let {
            it.send(buildQueueMessage())
        }
    }

    /**
     * Send the contents of the queue to all clients
     */
    fun sendQueueToEveryone() {
        broadcast(buildQueueMessage())
    }

    fun sendConnectedList() {
        val listMessage = StringBuilder("connected")
        clientsConnected.forEach {
            listMessage.append("\n")
            listMessage.append(it)
        }
        broadcast(listMessage.toString())
    }

    /**
     * Prepares the WebSocket message to update the state of the queue on clients
     */
    private fun buildQueueMessage(): String {
        val queue = TinyJukebox.createCopyOfQueue()
        val message = StringBuilder("queue")
        for(musicEntry in queue) {
            val music = musicEntry.music
            message.append("\n")
            val jsonObj = JsonObject()
            jsonObj.addProperty("title", music.name)
            jsonObj.addProperty("duration", music.duration)
            jsonObj.addProperty("locked", musicEntry.locked)
            jsonObj.addProperty("uploader", musicEntry.uploader)
            message.append(jsonObj.toString())
        }
        return message.toString()
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        val username = conn?.getAttachment<String>()
        synchronized(clientsConnected) {
            clientsConnected.remove(username)
            sendConnectedList()
        }
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        message?.let {
            when {
                message.startsWith("SessionId") -> {
                    val sessionID = message.split("\n")[1]
                    val session = Session.load(sessionID)
                    conn?.setAttachment(session.username)
                    synchronized(clientsConnected) {
                        println("${session.username} connected!")
                        clientsConnected += session.username
                        sendConnectedList()
                    }
                }

                message.startsWith("ytsearch") -> {
                    val query = message.split("\n")[1]
                    println("Loading results for query $query")
                    thread(isDaemon = true) {
                        val process = ProcessBuilder()
                        val ytdl = process.command("youtube-dl", "--simulate", "--print-json", "ytsearch10:$query").start()
                        val reader = BufferedReader(InputStreamReader(ytdl.inputStream))
                        val parser = JsonParser()
                        for(i in 0 until 10) {
                            val json = reader.readLine()
                            val obj = parser.parse(json).asJsonObject
                            val response = JsonObject()
                            response.addProperty("id", obj["id"].asString)
                            response.addProperty("title", obj["title"].asString)
                            response.addProperty("channel", obj["uploader"].asString)
                            val seconds = obj["duration"].asInt
                            val duration = (seconds / 60).toString() + ":" + String.format("%02d", seconds % 60)
                            response.addProperty("duration", duration)
                            conn!!.send("ytsearch\n$query\n$response\n")
                        }
                        reader.close()
                    }
                }

                else -> {
                    println("Unknown Websocket message $message")
                }
            }
        }
    }

    override fun onStart() {
        println("Websocket server started on $address!")
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        ex.printStackTrace()
    }

    /**
     * Send an update to all connected clients about the state of the music player.
     * @param actuallyPlaying is the player playing any music? Using 'false' allows to use nulls for all other parameters
     * @param name name of the music currently playing
     * @param position where are we in the playback? (Format is: <minutes>:<seconds>)
     * @param duration how long is the music? (Format is: <minutes>:<seconds>)
     * @param percent how much of the music has been played?
     */
    fun sendPlayerUpdate(actuallyPlaying: Boolean, name: String? = null, position: Long? = null, duration: Long? = null, percent: Double? = null, isLoading: Boolean = false) {
        val message = StringBuilder("playerUpdate")
        message.append('\n').append(actuallyPlaying)
        if(actuallyPlaying) {
            message.append('\n').append(name)
            message.append('\n').append(position)
            message.append('\n').append(duration)
            message.append('\n').append(percent)
            message.append('\n').append(isLoading)
        }
        broadcast(message.toString())
    }
}