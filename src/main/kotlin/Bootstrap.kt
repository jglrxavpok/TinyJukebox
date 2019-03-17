import org.jglrxavpok.tinyjukebox.HttpHandler
import org.jglrxavpok.tinyjukebox.TinyJukebox
import org.jglrxavpok.tinyjukebox.player.MusicPlayer
import org.jglrxavpok.tinyjukebox.websocket.JukeboxWebsocketServer
import org.jglrxavpok.tinyjukebox.websocket.QuoteThread
import java.net.InetSocketAddress
import java.net.ServerSocket

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
    QuoteThread.start() // thread to synchronize quote between clients
    while(true) {
        val client = httpSocket.accept()
        HttpHandler(client).start()
    }
}