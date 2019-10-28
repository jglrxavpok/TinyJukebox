package org.jglrxavpok.tinyjukebox.http.controllers

import io.github.magdkudama.krouter.RouteResponse
import org.jglrxavpok.tinyjukebox.TinyJukebox
import org.jglrxavpok.tinyjukebox.auth.Permissions
import org.jglrxavpok.tinyjukebox.http.Controller
import org.jglrxavpok.tinyjukebox.http.HttpInfo
import org.jglrxavpok.tinyjukebox.http.HttpResponse
import org.jglrxavpok.tinyjukebox.player.FileSource
import org.jglrxavpok.tinyjukebox.player.Music
import org.jglrxavpok.tinyjukebox.player.YoutubeSource
import org.jglrxavpok.tinyjukebox.templating.TJDatabase
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Paths
import java.util.*

/**
 * Controller responsible of responding to upload requests
 */
class UploadController(context: HttpInfo): Controller(context) {

    fun upload(): RouteResponse {
        println("hello")
        session.checkPermissions(Permissions.Upload)
        println("hello2")
        val fileSource = context.attributes["File-Source"]
        val music: Music? = when(fileSource) {
            "Local" -> uploadLocal(context.clientReader, context.attributes)
            "Youtube" -> uploadYoutube(context.clientReader, context.attributes)
            else -> null
        }
        return music?.let {
            TJDatabase.onMusicUpload(session, music)
            TinyJukebox.addToQueue(it, session.username)
            HttpResponse(200)
        } ?: throw IllegalArgumentException("Unknown File-Source attribute: $fileSource")
    }

    /**
     * Download a file sent by the client.
     * The file is encoded in Base64 and sent via 'clientReader'
     */
    private fun uploadLocal(clientReader: BufferedReader, attributes: Map<String, String>): Music? {
        val filename = attributes["File-Name"]
        if(filename == null) {
            TinyJukebox.sendError(IllegalArgumentException("No file name ?! Are you trying to break me ?! >:("))
            return null
        }

        // verifies that the client is not trying to access invalid files (eg by trying to access files in '../')
        val root = Paths.get("music/").toAbsolutePath()
        val localFilePath = Paths.get("music/$filename").toAbsolutePath()
        val f = localFilePath.toFile()
        if(!f.canonicalPath.startsWith(root.toFile().canonicalPath)) {
            println("[ERROR uploadLocal] Tried to play invalid file at $localFilePath")
            TinyJukebox.sendError(IllegalArgumentException("Invalid file location, are you trying to break me ? :("))
            return null
        }
        val file = File("./music/$filename")
        if(file.isDirectory) {
            TinyJukebox.sendError(IllegalArgumentException("Invalid file location, that's a directory"))
            return null
        }
        if(!file.parentFile.exists()) {
            file.parentFile.mkdirs() // TODO check error
        }

        // saves the downloaded file
        val target = BufferedOutputStream(FileOutputStream(file))
        val line = clientReader.readLine()
        val input = Base64.getMimeDecoder().decode(line.substringAfter(";base64,"))
        target.write(input)
        target.flush()
        target.close()

        val source = FileSource(file)
        return Music(
            file.nameWithoutExtension,
            source,
            source.computeDurationInMillis()
        )
    }

    private fun uploadYoutube(clientReader: BufferedReader, attributes: Map<String, String>): Music? {
        // simply create the source from the given url
        val url = clientReader.readLine()
        if(url.isBlank()) {
            return null
        }
        return Music(YoutubeSource(url))
    }
}