package org.jglrxavpok.tinyjukebox.http

import org.jglrxavpok.tinyjukebox.auth.Session
import java.io.*

/**
 * Context used by the HttpHandler thread and the router to communicate about clients
 */
data class HttpInfo(val writer: PrintWriter, val length: Long, val clientReader: BufferedReader,
                    val clientInput: InputStream, val attributes: Map<String, String>,
                    val cookies: Map<String, String>, val session: Session) {
    companion object {

        /**
         * Creates a fake instance of HttpInfo, used by TinyJukeboxRouter to handle static resources
         */
        fun createFake(): HttpInfo {
            val input = ByteArrayInputStream(byteArrayOf(0))
            return HttpInfo(PrintWriter(ByteArrayOutputStream()), -1, BufferedReader(InputStreamReader(input)), input, emptyMap(), emptyMap(), Session.Anonymous)
        }
    }
}
