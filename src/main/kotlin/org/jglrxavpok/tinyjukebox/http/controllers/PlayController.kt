package org.jglrxavpok.tinyjukebox.http.controllers

import io.github.magdkudama.krouter.RouteNotFoundException
import io.github.magdkudama.krouter.RouteResponse
import org.jetbrains.exposed.sql.transactions.transaction
import org.jglrxavpok.tinyjukebox.TinyJukebox
import org.jglrxavpok.tinyjukebox.auth.Permissions
import org.jglrxavpok.tinyjukebox.http.Controller
import org.jglrxavpok.tinyjukebox.http.HttpInfo
import org.jglrxavpok.tinyjukebox.http.HttpResponse
import org.jglrxavpok.tinyjukebox.templating.TJDatabase
import org.jglrxavpok.tinyjukebox.templating.checkMusicExists
import java.net.URLDecoder

/**
 * Controller responsible of playing a given music via a post
 */
class PlayController(httpInfo: HttpInfo): Controller(httpInfo) {

    fun play(name: String): RouteResponse {
        session.checkPermissions(listOf(Permissions.Upload))
        val music = URLDecoder.decode(name, "UTF-8")
        val exists = transaction {
            checkMusicExists(music)
        }
        if(exists) {
            val musicObj = TJDatabase.getSavedMusic(music)
            TJDatabase.onMusicUpload(session, musicObj)
            TinyJukebox.addToQueue(musicObj, session.username)
            return HttpResponse(200)
        } else {
            throw RouteNotFoundException("No music $music")
        }
    }
}
