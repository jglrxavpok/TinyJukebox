package org.jglrxavpok.tinyjukebox.http.controllers

import io.github.magdkudama.krouter.RouteResponse
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jglrxavpok.tinyjukebox.http.Controller
import org.jglrxavpok.tinyjukebox.http.HttpInfo
import org.jglrxavpok.tinyjukebox.templating.NameFrequencyPair
import org.jglrxavpok.tinyjukebox.templating.TJDatabase
import org.jglrxavpok.tinyjukebox.templating.User
import org.jglrxavpok.tinyjukebox.templating.checkUserExists
import java.net.URLDecoder

class UserController(context: HttpInfo): Controller(context) {

    fun show(username: String): RouteResponse {
        val user = URLDecoder.decode(username, "UTF-8")
        val exists = transaction {
            checkUserExists(user)
        }
        return if(exists) {
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
    }

    private fun getTopFavorites(favorites: List<ResultRow>): List<NameFrequencyPair> {
        return favorites.map { row ->
            NameFrequencyPair(row[TJDatabase.Favorites.music], row[TJDatabase.Favorites.timesPlayed])
        }
    }
}