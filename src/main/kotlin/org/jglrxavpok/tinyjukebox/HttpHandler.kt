package org.jglrxavpok.tinyjukebox

import html.htmlErrorCodeToName
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import java.nio.file.Paths
import kotlin.random.Random

class HttpHandler(val client: Socket): Thread("Client $client") {

    companion object {
        val quotes by lazy {
            val text = HttpHandler::class.java.getResourceAsStream("/quotes_intech.txt")?.reader()?.readText() ?: "No quotes :c"
            text.split('\n')
        }
    }
    val rootPath = Paths.get("/")
    val writer = PrintWriter(OutputStreamWriter(client.getOutputStream()))
    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
    val random = Random(System.currentTimeMillis())

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

    fun get(location: String) {
        // special cases
        when(location) {
            "/quote" -> {
                htmlError(200, "Content-Type: text/plain")
                writer.println(quotes.random(random))
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

    private fun serve(pageName: String) {
        val html = javaClass.getResourceAsStream(pageName)?.reader()?.readText() ?: return htmlError(404)
        htmlError(200)
        writer.println(html)
    }

    fun htmlError(errorCode: Int, vararg headerParameters: String) {
        writer.println("HTTP/1.1 $errorCode ${htmlErrorCodeToName[errorCode]}")
        for(param in headerParameters) {
            writer.println(param)
        }
        writer.println("")
    }


}