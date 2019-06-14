package org.jglrxavpok.tinyjukebox

import html.htmlErrorCodeToName
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jglrxavpok.tinyjukebox.auth.Session
import org.jglrxavpok.tinyjukebox.exceptions.InvalidSessionException
import org.jglrxavpok.tinyjukebox.templating.*
import org.jglrxavpok.tinyjukebox.websocket.QuoteThread
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import java.net.URLDecoder
import java.nio.file.Paths
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalTime
import org.jglrxavpok.tinyjukebox.templating.Text as TemplatingText

/**
 * Thread to handle HTTP requests from a client
 */
class HttpHandler(val client: Socket): Thread("HTTP Client $client") {

    companion object {
        /**
         * Name of the cookie holding the user's session id (when it exists)
         */
        const val SessionIdCookie = "SessionId"

        val TimeFormat = SimpleDateFormat("HH:mm:ss")
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

        // load session infos
        if(SessionIdCookie in cookies) {
            try {
                session = Session.load(cookies[SessionIdCookie]!!)
            } catch (e: InvalidSessionException) {
                session = Session.Anonymous
            }
        }

        if(location.startsWith("/action/")) {
            val actionType = location.substring("/action/".length)
            if(WebActions.isValidAction(actionType)) {
                htmlError(200)
                WebActions.perform(writer, actionType, length, reader, client.getInputStream(), attributes, cookies, session)
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
        // special cases
        when {
            location == "/quote" -> {
                htmlError(200, "Content-Type: text/plain; charset=utf-8")
                writer.println(QuoteThread.currentQuote)
                return
            }

            location.startsWith("/user/") -> {
                val user = URLDecoder.decode(location.substring("/user/".length), "UTF-8")
                println(">> $user")
                val exists = transaction {
                    checkUserExists(user)
                }
                if(exists) {
                    val userModel = hashMapOf<String, Any>()
                    val favorites = transaction {
                        TJDatabase.Favorites.select { TJDatabase.Favorites.user eq user }.orderBy(TJDatabase.Favorites.timesPlayed, SortOrder.DESC).take(3)
                    }

                    val none = NameFrequencyPair("NONE", 0)

                    fun NameFrequencyPair(row: ResultRow) = NameFrequencyPair(row[TJDatabase.Favorites.music], row[TJDatabase.Favorites.timesPlayed])

                    val first = if(favorites.isEmpty()) none else NameFrequencyPair(favorites[0])
                    val second = if(favorites.size < 2) none else NameFrequencyPair(favorites[1])
                    val third = if(favorites.size < 3) none else NameFrequencyPair(favorites[2])

                    userModel.put("user", User(user, Favorites(first, second, third)))

                    if(session.username == user) {
                        // TODO: show non-public info
                    }
                    serve("/users/user.html", userModel)
                } else {
                    val model = hashMapOf<String, Any>()
                    model["name"] = user
                    serve("/users/unknown.html", model)
                }
                return
            }

            location.startsWith("/music/") -> {
                val music = URLDecoder.decode(location.substring("/music/".length), "UTF-8")
                val exists = transaction {
                    checkMusicExists(music)
                }
                if(exists) {
                    val musicModel = hashMapOf<String, Any>()
                    val musicInfo = transaction { TJDatabase.Musics.select { TJDatabase.Musics.name eq music }.first() }
                    val duration = LocalTime.ofSecondOfDay(musicInfo[TJDatabase.Musics.length]/1000)
                    musicModel["music"] = MusicModel(music, musicInfo[TJDatabase.Musics.timesPlayedTotal], duration.toString())
                    serve("/musics/music.html", musicModel)
                } else {
                    val model = hashMapOf<String, Any>()
                    model["name"] = music
                    serve("/musics/unknown.html", model)
                }
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
            if(location == "/" || location == "index.html") {
                if(session == Session.Anonymous) {
                    "/landing.html"
                } else {
                    "/index.html"
                }
            } else {
                location
            }
        )
    }

    /**
     * Sends a given page or sends a 404 error
     */
    private fun serve(pageName: String, baseDataModel: Map<String, Any> = emptyMap()) {
        val resourceStream = javaClass.getResourceAsStream(pageName) ?: return htmlError(404)
        htmlError(200, type=getMimeFromExtension(pageName))
        if(pageName.endsWith(".png")) {
            client.getOutputStream().write(resourceStream.readBytes())
            client.getOutputStream().flush()
        } else if(pageName.endsWith(".html")) {
            val dataModel = hashMapOf<String, Any>()
            dataModel += baseDataModel
            if(session != Session.Anonymous) {
                dataModel["auth"] = Auth(session.username)
            }
            dataModel["text"] = TemplatingText(Config[Text.title])
            FreeMarker.processTemplate(pageName, dataModel, writer)
            //writer.println(applyVariables(text)) // TODO
        } else {
            val text = resourceStream.reader().readText()
            writer.println(applyVariables(text))
        }
    }

    private fun getMimeFromExtension(pageName: String): String {
        val extension = pageName.substringAfterLast(".")
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