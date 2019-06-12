package org.jglrxavpok.tinyjukebox

import html.htmlErrorCodeToName
import org.jglrxavpok.tinyjukebox.auth.Session
import org.jglrxavpok.tinyjukebox.exceptions.InvalidSessionException
import org.jglrxavpok.tinyjukebox.websocket.QuoteThread
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import java.nio.file.Paths

/**
 * Thread to handle HTTP requests from a client
 */
class HttpHandler(val client: Socket): Thread("HTTP Client $client") {

    companion object {
        /**
         * Name of the cookie holding the user's session id (when it exists)
         */
        const val SessionIdCookie = "SessionId"
    }

    /**
     * Path representing the root of the music folder
     */
    val rootPath = Paths.get("/")
    // Helper objects for communication
    val writer = PrintWriter(OutputStreamWriter(client.getOutputStream()))
    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
    private val cookies = HashMap<String, String>()

    private var session = Session.Anonymous

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

                if(parts[0] == "Cookie") {
                    val cookieInfo = parts[1].split("=")
                    cookies[cookieInfo[0]] = cookieInfo[1]
                }
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
                WebActions.perform(writer, actionType, length, reader, client.getInputStream(), attributes, cookies)
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

        do {
            val line = reader.readLine()
            if(line.startsWith("Cookie: ")) {
                val givenCookies = line.substring("Cookie: ".length).split("; ")
                for(cookie in givenCookies) {
                    val cookieInfo = cookie.split("=")
                    val name = cookieInfo[0]
                    val value = cookieInfo[1]
                    cookies[name] = value

                    println(">>> $name = $value")
                }
            }
        } while( ! line.isBlank())

        // load session infos
        if(SessionIdCookie in cookies) {
            try {
                session = Session.load(cookies[SessionIdCookie]!!)
            } catch (e: InvalidSessionException) {
                session = Session.Anonymous
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
        val resourceStream = javaClass.getResourceAsStream(pageName) ?: return htmlError(404)
        htmlError(200, type=getMimeFromExtension(pageName))
        if(pageName.endsWith(".png")) {
            client.getOutputStream().write(resourceStream.readBytes())
            client.getOutputStream().flush()
        } else {
            val text = resourceStream.reader().readText()
            writer.println(applyVariables(text))
        }
    }

    private fun getMimeFromExtension(pageName: String): String {
        val extension = pageName.substringAfterLast(".")
        println("MIME FOR $extension")
        return when(extension) {
            "css" -> "text/css"
            "js" -> "text/javascript"

            else -> "text/html"
        }
    }

    private fun applyVariables(text: String): String {
        return text.replace(Regex("___Template (?<VAR>.*?),(?<COND>.*?) Template___")) { result ->
            val varName = result.groups["VAR"]
            val condition = result.groups["COND"]
            if(varName != null && condition != null) {
                val value = evaluateCondition(condition.value)
                javaClass.getResourceAsStream("/${varName.value}_$value.html")?.bufferedReader()?.readText() ?: "NotFound(${varName.value})"
            } else {
                result.toString()
            }
        }.replace(Regex("___(?<VAR>.*?)___")) { result ->
            val varName = result.groups["VAR"]
            if(varName != null) {
                evaluateVariable(varName.value) ?: Config.getFromProperties(varName.value)
            } else {
                result.toString()
            }
        }
    }

    private fun evaluateVariable(name: String): String? = when(name) {
        "username" -> session.username
        else -> null
    }

    private fun evaluateCondition(condition: String): String = when(condition) {
        "logged in" -> {
            if(SessionIdCookie in cookies && Session.exists(cookies[SessionIdCookie]!!)) {
                "logged_in"
            } else {
                "not_logged_in"
            }
        }

        else -> condition // echo the condition
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