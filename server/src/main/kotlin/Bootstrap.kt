import fr.gpotter2.SSLServerSocketKeystoreFactory
import org.java_websocket.server.DefaultSSLWebSocketServerFactory
import org.jglrxavpok.tinyjukebox.*
import org.jglrxavpok.tinyjukebox.player.MusicPlayer
import org.jglrxavpok.tinyjukebox.websocket.JukeboxWebsocketServer
import org.jglrxavpok.tinyjukebox.websocket.QuoteThread
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLServerSocketFactory

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

    // start http(s) and websocket servers
    val websocket = JukeboxWebsocketServer(InetSocketAddress(Config[Network.websocketPort]))
    val httpSocket: ServerSocket
    httpSocket = if(Config[Security.useSSL]) {
        val path = Config[Security.sslCertificate]
        val password = Config[Security.sslCertificatePassword]
        val port = Config[Network.httpsPort]
        val sslContext = SSLServerSocketKeystoreFactory.getSSLContextWithCert(path, password, SSLServerSocketKeystoreFactory.ServerSecureType.TLSv1_2)
        val socketFactory = sslContext.serverSocketFactory as SSLServerSocketFactory
        websocket.setWebSocketFactory(DefaultSSLWebSocketServerFactory( sslContext ))
        socketFactory.createServerSocket(port) as SSLServerSocket
    } else {
        System.err.println("You are not using a secure connection! Consider creating your own certificate and setting 'Security.useSSL' to true in the configuration file")
        ServerSocket(Config[Network.httpsPort])
    }

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