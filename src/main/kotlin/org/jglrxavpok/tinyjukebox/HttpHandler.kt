package org.jglrxavpok.tinyjukebox

import html.htmlErrorCodeToName
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket


class HttpHandler(val client: Socket): Thread("Client $client") {

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
                WebActions.perform(actionType, length, reader, client.getInputStream(), attributes)
                htmlError(200)
            } else {
                htmlError(404)
            }
        } else {
            htmlError(404)
        }
    }

    fun get(location: String) {
        if(location != "/") {
            htmlError(404)
            return
        }
        htmlError(200)
        serve("index")
    }

    private fun serve(pageName: String) {
        val html = javaClass.getResourceAsStream("/$pageName.html").reader().readText()
        writer.println(html)
    }

    fun htmlError(errorCode: Int) {
        writer.println("HTTP/1.1 $errorCode ${htmlErrorCodeToName[errorCode]}")
        writer.println("")
    }


}