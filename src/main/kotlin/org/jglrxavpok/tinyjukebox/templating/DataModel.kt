package org.jglrxavpok.tinyjukebox.templating

import org.jglrxavpok.tinyjukebox.auth.Permissions

data class Auth(val username: String, val permissions: List<Permissions>)
data class Text(val title: String)

data class MusicModel(val name: String, val timesPlayed: Int, val timesSkipped: Int, val duration: String/* TODO: More information? */)

typealias NameFrequencyPair = Pair<String, Int>

data class User(val username: String, val favorites: Array<NameFrequencyPair>, val avatarURL: String? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        if (username != other.username) return false
        if (!favorites.contentEquals(other.favorites)) return false
        if (avatarURL != other.avatarURL) return false

        return true
    }

    override fun hashCode(): Int {
        var result = username.hashCode()
        result = 31 * result + favorites.contentHashCode()
        result = 31 * result + (avatarURL?.hashCode() ?: 0)
        return result
    }
}
