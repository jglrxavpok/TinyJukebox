package org.jglrxavpok.tinyjukebox.http

import html.htmlErrorCodeToName
import org.jglrxavpok.tinyjukebox.auth.Session
import org.jglrxavpok.tinyjukebox.exceptions.InvalidSessionException
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

    companion object {
        /**
         * Name of the cookie holding the user's session id (when it exists)
         */
        const val SessionIdCookie = "SessionId"
    }
    private val cookies = HashMap<String, String>()

    private var session = Session.Anonymous

    private fun readAttributes(): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        do {
            val line = reader.readLine()
            val parts = line.split(": ")
            if(parts.size > 1) {
                attributes[parts[0]] = parts[1]

                if(parts[0] == "Cookie") {
                    for(cookie in parts[1].split("; ")) {
                        val cookieInfo = cookie.split("=")
                        cookies[cookieInfo[0]] = cookieInfo[1]
                    }
                }
            }
            println(line) // TODO: debug only
        } while(line.isNotEmpty())


        // load session infos
        if(SessionIdCookie in cookies) {
            session = try {
                Session.load(cookies[SessionIdCookie]!!)
            } catch (e: InvalidSessionException) {
                Session.Anonymous
            }
        }
        return attributes
    }

    override fun run() {
        val request = reader.readLine() ?: return htmlError(400)
        val parts = request.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray()
        val type = parts[0]
        val location = parts[1].substringBefore("?") // drop GET arguments
        println("Received request $request")
        val attributes = readAttributes()
        val context = HttpInfo(writer, attributes["File-Length"]?.toLong() ?: -1, reader, client.getInputStream(), attributes, cookies, session)
        when(type) {
            "GET" -> TinyJukeboxRouter.get(location, context).write(client.getOutputStream(), writer)
            "POST" -> TinyJukeboxRouter.post(location, context).write(client.getOutputStream(), writer)
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