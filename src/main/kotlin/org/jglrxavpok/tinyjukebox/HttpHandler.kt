package org.jglrxavpok.tinyjukebox

import html.htmlErrorCodeToName
import org.jglrxavpok.tinyjukebox.websocket.QuoteThread
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import java.nio.file.Paths
import kotlin.random.Random

/**
 * Thread to handle HTTP requests from a client
 */
class HttpHandler(val client: Socket): Thread("HTTP Client $client") {

    /**
     * Path representing the root of the music folder
     */
    val rootPath = Paths.get("/")

    // Helper objects for communication
    val writer = PrintWriter(OutputStreamWriter(client.getOutputStream()))
    val reader = BufferedReader(InputStreamReader(client.getInputStream()))

    override fun run() {
        val request = reader.readLine()
        val parts = request.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray()
        val type = parts[0]
        val location = parts[1]
        println("Received request $request")
        when(type) {
            "GET" -> get(location)
            "POST" -> post(location)
        }
        writer.flush()
        writer.close()
        client.close()
    }

    /**
     * Handles a POST request
     * @param location the requested location
     */
    fun post(location: String) {
        // remove header info
        var length: Long = -1
        val attributes = mutableMapOf<String, String>()
        do {
            val line = reader.readLine()
            val parts = line.split(": ")
            if(parts.size > 1) {
                attributes[parts[0]] = parts[1]
            }
            if(line.startsWith("File-Size: ")) {
                length = line.substring("File-Size: ".length).toLong()
            }
            println(line) // TODO: debug only
        } while(line.isNotEmpty())


        if(location.startsWith("/action/")) {
            val actionType = location.substring("/action/".length)
            if(WebActions.isValidAction(actionType)) {
                htmlError(200)
                WebActions.perform(writer, actionType, length, reader, client.getInputStream(), attributes)
            } else {
                htmlError(404)
            }
        } else {
            htmlError(404)
        }
    }

    /**
     * Handles a GET request
     * @param location the requested location
     */
    fun get(location: String) {
        // special cases
        when(location) {
            "/quote" -> {
                htmlError(200, "Content-Type: text/plain; charset=utf-8")
                writer.println(QuoteThread.currentQuote)
                return
            }
        }

        // simply serving pages
        val newPath = Paths.get(location)
        val valid = newPath.startsWith(rootPath) // the path MUST be within the server
        if(!valid) {
            println(">> $newPath - $rootPath")
            serve("403")
            return
        }
        serve(
            if(location == "/") {
                "/index.html"
            } else {
                location
            }
        )
    }

    /**
     * Sends a given page or sends a 404 error
     */
    private fun serve(pageName: String) {
        val html = javaClass.getResourceAsStream(pageName)?.reader()?.readText() ?: return htmlError(404)
        htmlError(200)
        writer.println(html)
    }

    /**
     * Writes a HTTP header corresponding to the given error code with the given parameters
     */
    fun htmlError(errorCode: Int, vararg headerParameters: String) {
        writer.println("HTTP/1.1 $errorCode ${htmlErrorCodeToName[errorCode]}")
        writer.println("Content-Type: text/html; charset=utf-8")
        for(param in headerParameters) {
            writer.println(param)
        }
        writer.println("")
    }


}