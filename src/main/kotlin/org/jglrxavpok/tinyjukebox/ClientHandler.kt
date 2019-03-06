package org.jglrxavpok.tinyjukebox

import html.htmlErrorCodeToName
import html.html
import org.jglrxavpok.tinyjukebox.WebActions.prepareScripts
import org.jglrxavpok.tinyjukebox.player.MusicPlayer
import java.io.*
import java.net.Socket


class ClientHandler(val client: Socket): Thread("Client $client") {

    val writer = PrintWriter(OutputStreamWriter(client.getOutputStream()))
    val reader = BufferedReader(InputStreamReader(client.getInputStream()))

    override fun run() {
        val request = reader.readLine()
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

    fun post(location: String) {
        // remove header info
        var length: Long = -1
        val attributes = mutableMapOf<String, String>()
        do {
            val line = reader.readLine()
            val parts = line.split(": ")
            if(parts.size > 1) {
                attributes[parts[0]] = parts[1]
            }
            if(line.startsWith("File-Size: ")) {
                length = line.substring("File-Size: ".length).toLong()
            }
            println(line) // TODO: debug only
        } while(line.isNotEmpty())


        if(location.startsWith("/action/")) {
            val actionType = location.substring("/action/".length)
            if(WebActions.isValidAction(actionType)) {
                WebActions.perform(actionType, length, reader, client.getInputStream(), attributes)
                htmlError(200)
            } else {
                htmlError(404)
            }
        } else {
            htmlError(404)
        }
    }

    fun get(location: String) {
        if(location != "/") {
            htmlError(404)
            return
        }
        htmlError(200)
        val html = html {
            +"<!doctype html>"
            head {
                title { +"TinyJukebox" }
                +"<meta charset=\"UTF-8\" name=\"viewport\" content=\"width=device-width, initial-scale=1, shrink-to-fit=no\">"
                +"<link rel=\"stylesheet\" href=\"https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css\" integrity=\"sha384-ggOyR0iXCbMQv3Xipma34MD+dH/1fQ784/j6cY/iJTQUOhcWr7x9JvoRxT2MZw1T\" crossorigin=\"anonymous\">"
            }
            body {
                h1 { +"TinyJukebox - INTech RTC" }

                val clip = MusicPlayer.currentClip
                if(clip != null) {
                    val music = MusicPlayer.currentMusic
                    if(music != null) {
                        val position = clip.microsecondPosition.toMinutesAndSeconds()
                        val totalTime = clip.microsecondLength.toMinutesAndSeconds()
                        h2 { +"Currently playing: <i>${music.name}</i> ($position - $totalTime)"}
                    }
                }
                +"Here's the current queue:"
                +"<br/>"

                // TODO: change to table
                val queue = TinyJukebox.createCopyOfQueue()
                for(m in queue) {
                    +"${m.name} (at ${m.file.absolutePath})"
                    +"<hr/>"
                }

                +"<hr/>"
                +"<input type=\"file\" name=\"musicFile\" id=\"musicFile\" />"
                /*+"<form enctype=\"multipart/form-data\" method=\"post\" action=\"/action/upload/\">"
                    +"<input type=\"file\" name=\"musicFile\" id=\"musicFile\" />"

                    +"<input type=\"submit\" />"
                +"</form>"*/
                button("upload") {
                    +"Upload"
                } .apply {
                    attributes["class"] = "btn btn-primary btn-lg"
                }
                +"<div id=\"transferProgress\"></div>"
                +"<br/>"
                button("empty") {
                    +"Clear queue"
                }

            }
            prepareScripts()
            // Bootstrap scripts
            +"""
                <script src="https://code.jquery.com/jquery-3.3.1.slim.min.js" integrity="sha384-q8i/X+965DzO0rT7abK41JStQIAqVgRVzpbzo5smXKp4YfRvH+8abtTE1Pi6jizo" crossorigin="anonymous"></script>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.14.7/umd/popper.min.js" integrity="sha384-UO2eT0CpHqdSJQ6hJty5KVphtPhzWj9WO1clHTMGa3JDZwrnQq4sF86dIHNDz0W1" crossorigin="anonymous"></script>
                <script src="https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/js/bootstrap.min.js" integrity="sha384-JjSmVgyd0p3pXB1rRibZUAYoIIy6OrQ6VrjIEaFf/nJGzIxFDsf4x0xIM+B07jRM" crossorigin="anonymous"></script>
            """.trimIndent()
        }
        writer.println(html)
    }

    fun htmlError(errorCode: Int) {
        writer.println("HTTP/1.1 $errorCode ${htmlErrorCodeToName[errorCode]}")
        writer.println("")
    }

    private fun Long.toMinutesAndSeconds(): String {
        val seconds = this / 1_000_000
        val minutes = seconds / 60
        return "$minutes:${String.format("%02d", seconds % 60)}"
    }
}