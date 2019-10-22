package org.jglrxavpok.tinyjukebox.http

import html.htmlErrorCodeToName
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jglrxavpok.tinyjukebox.*
import org.jglrxavpok.tinyjukebox.auth.Session
import org.jglrxavpok.tinyjukebox.exceptions.InvalidSessionException
import org.jglrxavpok.tinyjukebox.templating.*
import org.jglrxavpok.tinyjukebox.websocket.QuoteThread
import org.jtwig.JtwigModel
import java.io.*
import java.net.Socket
import java.net.URLDecoder
import java.nio.file.Paths
import java.time.LocalTime
import org.jglrxavpok.tinyjukebox.templating.Text as TemplatingText
import org.jtwig.JtwigTemplate



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
        val request = reader.readLine() ?: return htmlError(400)
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
                    for(cookie in parts[1].split("; ")) {
                        val cookieInfo = cookie.split("=")
                        cookies[cookieInfo[0]] = cookieInfo[1]
                    }
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

        when {
            location.startsWith("/action/") -> {
                val actionType = location.substring("/action/".length)
                if (WebActions.isValidAction(actionType)) {
                    htmlError(200)
                    val httpInfo = HttpInfo(writer, length, reader, client.getInputStream(), attributes, cookies, session)
                    WebActions.perform(httpInfo, actionType)
                } else {
                    htmlError(404)
                }
            }

            location.startsWith("/play/") -> {
                val music = URLDecoder.decode(location.substring("/play/".length), "UTF-8")
                val exists = transaction {
                    checkMusicExists(music)
                }
                if(exists) {
                    htmlError(200)
                    val musicObj = TJDatabase.getSavedMusic(music)
                    TJDatabase.onMusicUpload(session, musicObj)
                    TinyJukebox.addToQueue(musicObj)
                } else {
                    htmlError(404)
                }
            }

            else -> htmlError(404)
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

            location == "/musics" || location == "/musics/" || location == "/music" || location == "/music/" -> {
                val musics = transaction {
                    TJDatabase.Musics.selectAll()
                        .map { musicInfo ->
                            val duration = LocalTime.ofSecondOfDay(musicInfo[TJDatabase.Musics.length]/1000)
                            MusicModel(musicInfo[TJDatabase.Musics.name], musicInfo[TJDatabase.Musics.timesPlayedTotal],
                                musicInfo[TJDatabase.Musics.timesSkippedTotal], duration.toString())
                        }.toList().sortedByDescending { it.timesPlayed }
                }
                val model = hashMapOf<String, Any>()
                model["musics"] = musics
                serve("/musics/musics.html.twig", model)
                return
            }

            location.startsWith("/user/") -> {
                val user = URLDecoder.decode(location.substring("/user/".length), "UTF-8")
                val exists = transaction {
                    checkUserExists(user)
                }
                if(exists) {
                    val userModel = hashMapOf<String, Any>()
                    val favorites = transaction {
                        TJDatabase.Favorites.select { TJDatabase.Favorites.user eq user }.orderBy(
                            TJDatabase.Favorites.timesPlayed, SortOrder.DESC).take(3)
                    }

                    val none = NameFrequencyPair("NONE", 0)

                    fun NameFrequencyPair(row: ResultRow) = NameFrequencyPair(row[TJDatabase.Favorites.music], row[TJDatabase.Favorites.timesPlayed])

                    val first = if(favorites.isEmpty()) none else NameFrequencyPair(favorites[0])
                    val second = if(favorites.size < 2) none else NameFrequencyPair(favorites[1])
                    val third = if(favorites.size < 3) none else NameFrequencyPair(favorites[2])

                    userModel["user"] = User(user, Favorites(first, second, third))

                    if(session.username == user) {
                        // TODO: show non-public info
                    }
                    serve("/users/user.html.twig", userModel)
                } else {
                    val model = hashMapOf<String, Any>()
                    model["name"] = user
                    serve("/users/unknown.html.twig", model)
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
                    musicModel["music"] = MusicModel(music, musicInfo[TJDatabase.Musics.timesPlayedTotal], musicInfo[TJDatabase.Musics.timesSkippedTotal], duration.toString())
                    serve("/musics/music.html.twig", musicModel)
                } else {
                    val model = hashMapOf<String, Any>()
                    model["name"] = music
                    serve("/musics/unknown.html.twig", model)
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
                    "/landing.html.twig"
                } else {
                    "/index.html.twig"
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
        try {
            val resourceStream = javaClass.getResourceAsStream(pageName) ?: return htmlError(404)
            htmlError(200, type=getMIME(pageName))
            println("Serving $pageName")
            when {
                pageName.endsWith(".png") -> {
                    writer.flush()
                    client.getOutputStream().write(resourceStream.readBytes())
                    client.getOutputStream().flush()
                }
                pageName.endsWith(".twig") -> {
                    val dataModel = hashMapOf<String, Any>()
                    dataModel += baseDataModel
                    if(session != Session.Anonymous) {
                        dataModel["auth"] = Auth(session.username, TJDatabase.getPermissions(session.username))
                    }
                    dataModel["text"] = TemplatingText(Config[Text.title])

                    val template = JtwigTemplate.classpathTemplate(pageName)
                    val model = JtwigModel.newModel(dataModel)
                    val out = ByteArrayOutputStream()
                    template.render(model, out)
                    writer.println(String(out.toByteArray()))
                    out.close()
                }
                else -> {
                    val text = resourceStream.reader().readText()
                    writer.println(applyVariables(text))
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Error while serving $pageName", e)
        }
    }

    private fun getMIME(pageName: String): String {
        val extension = pageName.substringAfterLast(".")
        try {
            return when(extension) {
                "css" -> "text/css"
                "js" -> "text/javascript"
                "png" -> "image/png"
                "html" -> "text/html"

                "eot" -> "application/vnd.ms-fontobject"
                "woff" -> "application/font-woff"
                "woff2" -> "application/font-woff2"
                "ttf" -> "application/x-font-truetype"
                "svg" -> "image/svg+xml"
                "otf" -> "application/x-font-opentype"
                "twig" -> getMIME(pageName.substringBeforeLast("."))

                else -> javaClass.getResource(pageName).openConnection().contentType
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return "text/html"
        }
    }

    private fun applyVariables(text: String): String {
        return text.replace(Regex("___(?<VAR>.*?)___")) { result ->
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