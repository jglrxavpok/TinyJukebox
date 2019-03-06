package org.jglrxavpok.tinyjukebox.websocket

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.jglrxavpok.tinyjukebox.TinyJukebox
import java.lang.Exception
import java.lang.StringBuilder
import java.net.InetSocketAddress

class JukeboxWebsocketServer(address: InetSocketAddress): WebSocketServer(address) {
    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        conn?.send("Welcome!")
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

    fun sendQueueToEveryone() {
        broadcast(buildQueueMessage())
    }

    private fun buildQueueMessage(): String {
        val queue = TinyJukebox.createCopyOfQueue()
        val message = StringBuilder("queue")
        for(music in queue) {
            message.append("\n")
            message.append(music.name)
        }
        return message.toString()
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {

    }

    override fun onMessage(conn: WebSocket?, message: String?) {

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
     */
    fun sendPlayerUpdate(actuallyPlaying: Boolean, name: String? = null, position: String? = null, duration: String? = null) {
        val message = StringBuilder("playerUpdate")
        message.append('\n').append(actuallyPlaying)
        if(actuallyPlaying) {
            message.append('\n').append(name)
            message.append('\n').append(position)
            message.append('\n').append(duration)
        }
        broadcast(message.toString())
    }
}