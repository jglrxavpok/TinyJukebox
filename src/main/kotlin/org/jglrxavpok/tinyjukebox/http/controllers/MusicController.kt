package org.jglrxavpok.tinyjukebox.http.controllers

import io.github.magdkudama.krouter.RouteResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jglrxavpok.tinyjukebox.http.Controller
import org.jglrxavpok.tinyjukebox.http.HttpInfo
import org.jglrxavpok.tinyjukebox.templating.MusicModel
import org.jglrxavpok.tinyjukebox.templating.TJDatabase
import org.jglrxavpok.tinyjukebox.templating.checkMusicExists
import java.net.URLDecoder
import java.time.LocalTime

/**
 * Controller responsible of serving music pages (index and music info)
 */
class MusicController(context: HttpInfo): Controller(context) {

    fun index(): RouteResponse {
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
        return serve("/musics/musics.html.twig", model)
    }

    fun show(musicName: String): RouteResponse {
        val music = URLDecoder.decode(musicName, "UTF-8")
        val exists = transaction {
            checkMusicExists(music)
        }
        return if(exists) {
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
    }
}