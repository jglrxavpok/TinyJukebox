package org.jglrxavpok.tinyjukebox.http

import org.jglrxavpok.tinyjukebox.auth.Session
import java.io.*

data class HttpInfo(val writer: PrintWriter, val length: Long, val clientReader: BufferedReader,
                    val clientInput: InputStream, val attributes: Map<String, String>,
                    val cookies: Map<String, String>, val session: Session) {
    companion object {
        fun empty(): HttpInfo {
            val input = ByteArrayInputStream(byteArrayOf(0))
            return HttpInfo(PrintWriter(ByteArrayOutputStream()), -1, BufferedReader(InputStreamReader(input)), input, emptyMap(), emptyMap(), Session.Anonymous)
        }
    }
}
