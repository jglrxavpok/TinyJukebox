import org.jglrxavpok.tinyjukebox.ClientHandler
import org.jglrxavpok.tinyjukebox.player.MusicPlayer
import java.net.ServerSocket
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    MusicPlayer.start()
    val socket = ServerSocket(8080)
    while(true) {
        val client = socket.accept()
        thread {
            ClientHandler(client).start()
        }
    }
}