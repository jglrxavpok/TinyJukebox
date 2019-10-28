package org.jglrxavpok.tinyjukebox.templating

import org.jglrxavpok.tinyjukebox.Config
import org.jglrxavpok.tinyjukebox.auth.Permissions

data class Auth(val username: String, val permissions: List<Permissions>)

class Text {
    val title get()= Config[org.jglrxavpok.tinyjukebox.Text.title]
}

class Network {
    val websocketPort get()= Config[org.jglrxavpok.tinyjukebox.Network.websocketPort]
    val httpsPort get()= Config[org.jglrxavpok.tinyjukebox.Network.httpsPort]
    val httpPort get()= Config[org.jglrxavpok.tinyjukebox.Network.httpPort]
}

class Security {
    val rsaKeystore get()= Config[org.jglrxavpok.tinyjukebox.Security.rsaKeystore]
    val useSSL get()= Config[org.jglrxavpok.tinyjukebox.Security.useSSL]
    val httpsCertificate get()= Config[org.jglrxavpok.tinyjukebox.Security.httpsCertificate]
    val rsaUser get()= Config[org.jglrxavpok.tinyjukebox.Security.rsaUser]
    val wssCertificate get()= Config[org.jglrxavpok.tinyjukebox.Security.wssCertificate]
}

class Timings {
    val quoteFadeIn get()= Config[org.jglrxavpok.tinyjukebox.Timings.quoteFadeIn]
    val quoteFadeOut get()= Config[org.jglrxavpok.tinyjukebox.Timings.quoteFadeOut]
    val quoteDelay get()= Config[org.jglrxavpok.tinyjukebox.Timings.quoteDelay]
    val sessionExpiration get()= Config[org.jglrxavpok.tinyjukebox.Timings.sessionExpiration]
}

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
