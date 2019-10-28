import fr.gpotter2.SSLServerSocketKeystoreFactory
import org.java_websocket.server.DefaultSSLWebSocketServerFactory
import org.jglrxavpok.tinyjukebox.*
import org.jglrxavpok.tinyjukebox.auth.RSALoadKeyOrCreate
import org.jglrxavpok.tinyjukebox.http.HttpHandler
import org.jglrxavpok.tinyjukebox.player.MusicPlayer
import org.jglrxavpok.tinyjukebox.templating.TJDatabase
import org.jglrxavpok.tinyjukebox.websocket.JukeboxWebsocketServer
import org.jglrxavpok.tinyjukebox.websocket.QuoteThread
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLServerSocketFactory
import kotlin.concurrent.thread

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
    println("Loading Database...")
    TJDatabase.init()
    println("Finished loading!")

    MusicPlayer.start() // start Music playing thread

    println("Starting sockets")
    // start http(s) and websocket servers
    val websocket = JukeboxWebsocketServer(InetSocketAddress(Config[Network.websocketPort]))
    val httpSocket: ServerSocket
    httpSocket = if(Config[Security.useSSL]) {
        val pathHTTPS = Config[Security.httpsCertificate]
        val passwordHTTPS = Config[Security.httpsCertificatePassword]
        val pathWSS = Config[Security.wssCertificate]
        val passwordWSS = Config[Security.wssCertificatePassword]
        val port = Config[Network.httpsPort]
        val sslContextHTTPS = SSLServerSocketKeystoreFactory.getSSLContextWithCert(pathHTTPS, passwordHTTPS, SSLServerSocketKeystoreFactory.ServerSecureType.TLSv1_2)
        val sslContextWSS = SSLServerSocketKeystoreFactory.getSSLContextWithCert(pathWSS, passwordWSS, SSLServerSocketKeystoreFactory.ServerSecureType.TLSv1_2)
        val socketFactory = sslContextHTTPS.serverSocketFactory as SSLServerSocketFactory
        websocket.setWebSocketFactory(DefaultSSLWebSocketServerFactory( sslContextWSS ))
        socketFactory.createServerSocket(port) as SSLServerSocket
    } else {
        System.err.println("You are not using a secure connection! TinyJukebox always creates a RSA public/private key pair for communication but consider creating your own certificate and setting 'Security.useSSL' to true in the configuration file")
        ServerSocket(Config[Network.httpPort])
    }
    httpSocket.reuseAddress = true
    websocket.isReuseAddr = true

    RSALoadKeyOrCreate(Config[Security.rsaKeystore])

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

    thread(name = "Client acceptor", isDaemon = false) {
        while(true) {
            val client = httpSocket.accept()
            HttpHandler(client).start()
        }
    }

    // accept commands
    while(true) {
        val line = readLine()!!
        val parts = line.trim().split(" ")
        Commands.execute(parts)
    }
}
