package org.jglrxavpok.tinyjukebox.http

import html.htmlErrorCodeToName
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jglrxavpok.tinyjukebox.Config
import org.jglrxavpok.tinyjukebox.Debug
import org.jglrxavpok.tinyjukebox.Text
import org.jglrxavpok.tinyjukebox.TinyJukebox
import org.jglrxavpok.tinyjukebox.auth.Session
import org.jglrxavpok.tinyjukebox.exceptions.InvalidSessionException
import org.jglrxavpok.tinyjukebox.templating.*
import org.jglrxavpok.tinyjukebox.websocket.QuoteThread
import org.jtwig.JtwigModel
import org.jtwig.JtwigTemplate
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.net.Socket
import java.net.URLDecoder
import java.nio.file.Paths
import java.time.LocalTime

/**
 * Serves pages or perform actions based on the location asked by the client
 */
class Router(val client: Socket, val reader: BufferedReader, val writer: PrintWriter) {

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
    private val cookies = HashMap<String, String>()

    internal var session = Session.Anonymous

    /**
     * Handles a POST request
     * @param location the requested location
     */
    fun post(location: String) {
        // remove header info
        val attributes = readAttributes()
        var length = attributes["File-Size"]?.toLong() ?: -1

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
     * Extracts HTTP attributes from header
     */
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

    /**
     * Handles a GET request
     * @param location the requested location
     */
    fun get(location: String) {
        readAttributes()

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
                            TJDatabase.Favorites.timesPlayed, SortOrder.DESC).take(10)
                    }

                    userModel["user"] = User(user, getTopFavorites(favorites).toTypedArray())

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
                "/index.html.twig"
            } else {
                location
            }
        )
    }

    private fun getTopFavorites(favorites: List<ResultRow>): List<NameFrequencyPair> {
        return favorites.map { row ->
            NameFrequencyPair(row[TJDatabase.Favorites.music], row[TJDatabase.Favorites.timesPlayed])
        }
    }

    /**
     * Sends a given page or sends a 404 error
     */
    private fun serve(pageName: String, baseDataModel: Map<String, Any> = emptyMap()) {
        try {
            val resourceStream = javaClass.getResourceAsStream(pageName) ?: return htmlError(404)
            htmlError(200, type=getMIME(pageName))
            println("Serving $pageName")
            val dataModel = hashMapOf<String, Any>()
            dataModel += baseDataModel
            dataModel["debugMode"] = Config[Debug.enabled]
            when {
                pageName.endsWith(".png") -> {
                    writer.flush()
                    client.getOutputStream().write(resourceStream.readBytes())
                    client.getOutputStream().flush()
                }
                pageName.endsWith(".twig") -> {
                    if(session != Session.Anonymous) {
                        dataModel["auth"] = Auth(session.username, TJDatabase.getPermissions(session.username))
                    }
                    dataModel["text"] = Text(Config[Text.title])

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

    /**
     * Tries to find the MIME type of a given resource
     */
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