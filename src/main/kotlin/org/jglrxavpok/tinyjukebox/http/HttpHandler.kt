package org.jglrxavpok.tinyjukebox.http

import html.htmlErrorCodeToName
import org.jglrxavpok.tinyjukebox.auth.Session
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket


/**
 * Thread to handle HTTP requests from a client
 */
class HttpHandler(val client: Socket): Thread("HTTP Client $client") {

    // Helper objects for communication
    val writer = PrintWriter(OutputStreamWriter(client.getOutputStream()))
    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
    private val cookies = HashMap<String, String>()

    private var session = Session.Anonymous

    override fun run() {
        val request = reader.readLine() ?: return htmlError(400)
        val parts = request.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray()
        val type = parts[0]
        val location = parts[1]
        println("Received request $request")
        when(type) {
            "GET" -> Router(client, reader, writer).get(location)
            "POST" -> Router(client, reader, writer).post(location)
        }
        writer.flush()
        writer.close()
        client.close()
    }

    /**
     * Writes a HTTP header corresponding to the given error code with the given parameters
     */
    fun htmlError(errorCode: Int, type: String = "text/html", vararg headerParameters: String) {
        writer.println("HTTP/1.1 $errorCode ${htmlErrorCodeToName[errorCode]}")
        writer.println("Content-Type: $type; charset=utf-8")
        for(param in headerParameters) {
            writer.println(param)
        }
        writer.println("")
    }

}