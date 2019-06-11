import org.jglrxavpok.tinyjukebox.Config
import org.jglrxavpok.tinyjukebox.HttpHandler
import org.jglrxavpok.tinyjukebox.TinyJukebox
import org.jglrxavpok.tinyjukebox.player.MusicPlayer
import org.jglrxavpok.tinyjukebox.websocket.JukeboxWebsocketServer
import org.jglrxavpok.tinyjukebox.websocket.QuoteThread
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * TinyJukebox entry point
 */
fun main() {
    val fileOut = FileOutputStream("tinyjukebox.log")
    val fileErr = FileOutputStream("errors.log")
    val stdout = System.out
    val stderr = System.err
    val dualOut = object: OutputStream() {
        override fun write(b: Int) {
            stdout.write(b)
            fileOut.write(b)
        }

        override fun flush() {
            fileOut.flush()
            stdout.flush()
        }
    }
    val dualErr = object: OutputStream() {
        override fun write(b: Int) {
            stderr.write(b)
            fileErr.write(b)
        }

        override fun flush() {
            fileErr.flush()
            stderr.flush()
        }
    }
    System.setOut(PrintStream(dualOut, true))
    System.setErr(PrintStream(dualErr, true))
    Config.load()
    MusicPlayer.start() // start Music playing thread

    // start http and websocket servers
    // TODO: configurable port
    val httpSocket = ServerSocket(8080)
    val websocket = JukeboxWebsocketServer(InetSocketAddress(8887))
    TinyJukebox.setWebsocket(websocket)
    websocket.start()
    QuoteThread.start() // thread to synchronize quote between clients

    val shutdownThread = object: Thread() {
        override fun run() {
            websocket.stop()
            httpSocket.close()
        }
    }
    Runtime.getRuntime().addShutdownHook(shutdownThread)
    while(true) {
        val client = httpSocket.accept()
        HttpHandler(client).start()
    }
}