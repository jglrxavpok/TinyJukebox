import org.jglrxavpok.tinyjukebox.HttpHandler
import org.jglrxavpok.tinyjukebox.TinyJukebox
import org.jglrxavpok.tinyjukebox.player.MusicPlayer
import org.jglrxavpok.tinyjukebox.websocket.JukeboxWebsocketServer
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * TinyJukebox entry point
 */
fun main(args: Array<String>) {
    MusicPlayer.start() // start Music playing thread

    // start http and websocket servers
    // TODO: configurable port
    val httpSocket = ServerSocket(8080)
    val websocket = JukeboxWebsocketServer(InetSocketAddress(8887))
    TinyJukebox.setWebsocket(websocket)
    websocket.start()
    while(true) {
        val client = httpSocket.accept()
        HttpHandler(client).start()
    }
}