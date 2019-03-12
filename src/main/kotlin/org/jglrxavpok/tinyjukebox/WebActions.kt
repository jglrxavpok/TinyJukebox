package org.jglrxavpok.tinyjukebox

import java.io.*
import java.lang.Exception
import java.net.URLEncoder
import java.nio.file.Paths
import java.util.*

object WebActions {

    open class Action(val id: String, val reloadsPage: Boolean, val action: (Long, BufferedReader, InputStream, Map<String, String>) -> Unit, val generateParameters: () -> String? = {"return null"}) {
        open fun generateSend(): String {
            return """
                var content = function() {
                    ${generateParameters()}
                }
                xhttp.send(content());
            """.trimIndent()
        }
    }

    val id2actionMap = listOf(
        Action("empty", true, this::empty),
        object: Action("upload", true, this::upload, {null}) {
            override fun generateSend(): String {
                return """
                    var field = document.getElementById("musicFile");
                    var file = field.files[0];
                    console.log("my object: %o", file)
                    xhttp.setRequestHeader("File-Size", file.size);
                    xhttp.setRequestHeader("File-Name", file.name);

                    var transferDiv = document.getElementById("transferProgress");
                    transferDiv.innerHTML = "Loading file...";
                    var reader = new FileReader();
                    reader.onload = function(e) {
                        var arrayBuffer = reader.result;
                        transferDiv.innerHTML = "Sending!";
                        xhttp.send(arrayBuffer+"\n");
                        //console.log(arrayBuffer);
                    }

                 //   reader.readAsArrayBuffer(file);
                 reader.readAsDataURL(file);
                """.trimIndent()

            }
        }
    )

    private fun empty(length: Long, clientReader: BufferedReader, clientInput: InputStream, attributes: Map<String, String>) {
        TinyJukebox.emptyQueue()
    }

    private fun upload(length: Long, clientReader: BufferedReader, clientInput: InputStream, attributes: Map<String, String>) {
        val fileSource = attributes["File-Source"]
        val music: Music? = when(fileSource) {
            "Local" -> uploadLocal(clientReader, attributes)
            "Youtube" -> uploadYoutube(clientReader, attributes)
            else -> null
        }
        music?.let {
            TinyJukebox.addToQueue(it)
        }
    }

    private fun uploadLocal(clientReader: BufferedReader, attributes: Map<String, String>): Music? {
        val filename = attributes["File-Name"]
        if(filename == null) {
            TinyJukebox.sendError(IllegalArgumentException("No file name ?! Are you trying to break me ?! >:("))
            return null
        }
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
        val target = BufferedOutputStream(FileOutputStream(file))

        val line = clientReader.readLine()
        val input = Base64.getMimeDecoder().decode(line.substringAfter(";base64,"))
        target.write(input)
        target.flush()
        target.close()

        println("file size=${file.length()}")
        return Music(file.nameWithoutExtension, file)
    }

    private fun uploadYoutube(clientReader: BufferedReader, attributes: Map<String, String>): Music? {
        val url = clientReader.readLine()
        println("Attempting to read music from YT url: $url")
        val ytExtractsFolder = File("./music/yt/")
        if(!ytExtractsFolder.exists()) {
            ytExtractsFolder.mkdirs()
        }
        val process = ProcessBuilder()
        val tmpLogFile = File("./music/yt/tmp_${System.currentTimeMillis()}.log")
        val ytdl = process.directory(ytExtractsFolder).command("youtube-dl", "-x", "--audio-format", "mp3", url)
        println(ytdl.command().joinToString(" "))
        ytdl.redirectOutput(tmpLogFile)
        val exitStatus = ytdl.start().waitFor()
        val logFile = tmpLogFile.readText()
        println(logFile)
        val destination: String = extractDestination(logFile)
        //tmpLogFile.delete()
        val file = File(ytExtractsFolder, destination)
        println("destination file: ${file.absolutePath}")
        val newFile = File(ytExtractsFolder, destination.substringBeforeLast("-")+".mp3")
        val renameSuccessful = file.renameTo(newFile)
        println(">>>> download $exitStatus")
        if(exitStatus == 0) {
            return if(renameSuccessful) {
                Music(newFile.nameWithoutExtension, newFile)
            } else {
                Music(file.nameWithoutExtension, file)
            }
        }
        return null
    }

    private fun extractDestination(logFile: String): String {
        val prefix = "[download] Destination: "
        return logFile.lines().first {
            it.startsWith(prefix)
        }.substring(prefix.length).substringBeforeLast(".")+".mp3"
    }

    fun perform(actionType: String, length: Long, reader: BufferedReader, clientInput: InputStream, attributes: Map<String, String>) {
        try {
            id2actionMap.first { it.id == actionType}.action(length, reader, clientInput, attributes)
        } catch (e: Exception) {
            TinyJukebox.sendError(IllegalArgumentException("Failed to perform action: $e"))
            e.printStackTrace()
        }
    }

    fun isValidAction(actionType: String): Boolean {
        return id2actionMap.any { it.id == actionType }
    }

}