package org.jglrxavpok.tinyjukebox.templating

import org.jglrxavpok.tinyjukebox.auth.Permissions

data class Auth(val username: String, val permissions: List<Permissions>)
data class Text(val title: String)

typealias NameFrequencyPair = Pair<String, Int>
data class Favorites(val first: NameFrequencyPair, val second: NameFrequencyPair, val third: NameFrequencyPair)
data class User(val username: String, val favorites: Favorites, val avatarURL: String? = null)

data class MusicModel(val name: String, val timesPlayed: Int, val timesSkipped: Int, val duration: String/* TODO: More information? */)