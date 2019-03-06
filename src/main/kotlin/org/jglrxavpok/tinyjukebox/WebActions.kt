package org.jglrxavpok.tinyjukebox

import html.HTML
import java.io.*
import java.lang.StringBuilder
import java.util.*
import java.util.zip.DeflaterInputStream

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

    fun HTML.prepareScripts() {
        val sourceCode = StringBuilder()
        for(action in id2actionMap) {
            val id = action.id
            val reloadsPage = action.reloadsPage
            // TODO: identifiants
            val js = """
                function $id() {
                    var xhttp = new XMLHttpRequest();
                    xhttp.upload.addEventListener("progress", updateProgress, false);
                    xhttp.upload.addEventListener("load", transferComplete, false);
                    xhttp.upload.addEventListener("error", transferFailed, false);
                    xhttp.upload.addEventListener("abort", transferCanceled, false);

                    xhttp.open("POST", "/action/$id", true);

                    var transferDiv = document.getElementById("transferProgress");
                    // downloading
                    function updateProgress (oEvent) {
                      if (oEvent.lengthComputable) {
                        var percentComplete = oEvent.loaded / oEvent.total;
                        var percent = Math.round(percentComplete*100);
                        transferDiv.innerHTML =
                        "<div class=\"progress\"><div class=\"progress-bar\" role=\"progressbar\" aria-valuenow=percent aria-valuemin=\"0\" aria-valuemax=\"100\"></div></div>";
                      } else {
                        // Unknown size
                      }
                    }

                    function transferComplete(evt) {
                        transferDiv.innerHTML = "Complete!";
                        ${
                            if(reloadsPage) { // reloads the page
                                "document.location.reload(true); // reload the page"
                            } else {
                                "// does not reload the page"
                            }
                        }
                    }

                    function transferFailed(evt) {
                      transferDiv.innerHTML = "Failed :(";
                    }

                    function transferCanceled(evt) {
                      transferDiv.innerHTML = "Cancelled";
                    }
                    xhttp.setRequestHeader("Content-Type", "application/octet-stream");
                    ${action.generateSend()}
                }

                if(document.getElementById("$id")) {
                    document.getElementById("$id").onclick = $id;
                }

            """.trimIndent()
            sourceCode.append(js).append("\n")
        }
        script("text/javascript", sourceCode.toString())
    }

    private fun empty(length: Long, clientReader: BufferedReader, clientInput: InputStream, attributes: Map<String, String>) {
        TinyJukebox.emptyQueue()
    }

    private fun upload(length: Long, clientReader: BufferedReader, clientInput: InputStream, attributes: Map<String, String>) {
        var readCount: Int
        var totalCount = 0L
        val filename = attributes["File-Name"]!!
        val file = File("./music/$filename")
        if(!file.parentFile.exists()) {
            file.parentFile.mkdirs() // TODO check error
        }
        val target = BufferedOutputStream(FileOutputStream(file))
        val buffer = ByteArray(1024*16) // TODO: configurable buffer size

        val line = clientReader.readLine()
        val input = Base64.getMimeDecoder().decode(line.substringAfter(";base64,"))
        target.write(input)
/*        do {
            readCount = input.read(buffer, 0, minOf(clientInput.available(), buffer.size))
            if(readCount > 0) {
                target.write(buffer)
                totalCount += readCount.toLong()
                println(">> $totalCount")
            }
        } while(readCount > 0)
        println("read $totalCount - expected $length - diff is ${length-totalCount}")
 */       target.flush()
        target.close()

        println("file size=${file.length()}")
        val music = Music(filename.substringBeforeLast("."), file)
        TinyJukebox.addToQueue(music)
    }

    fun perform(actionType: String, length: Long, reader: BufferedReader, clientInput: InputStream, attributes: Map<String, String>) {
        id2actionMap.first { it.id == actionType}.action(length, reader, clientInput, attributes)
    }

    fun isValidAction(actionType: String): Boolean {
        return id2actionMap.any { it.id == actionType }
    }

}