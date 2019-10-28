package org.jglrxavpok.tinyjukebox.http

import org.jglrxavpok.tinyjukebox.Config
import org.jglrxavpok.tinyjukebox.Debug
import org.jglrxavpok.tinyjukebox.Text
import org.jglrxavpok.tinyjukebox.auth.Session
import org.jglrxavpok.tinyjukebox.templating.Auth
import org.jglrxavpok.tinyjukebox.templating.TJDatabase
import org.jtwig.JtwigModel
import org.jtwig.JtwigTemplate
import java.io.ByteArrayOutputStream

/**
 * Base Http controller. Used to control what TinyJukebox is supposed to do when reaching a route
 * @see TinyJukeboxRouter
 */
open class Controller(val context: HttpInfo) {

    /**
     * Session of the current client
     */
    val session: Session get()= context.session

    /**
     * Sends a given page or sends a 404 error
     */
    fun serve(pageName: String, baseDataModel: Map<String, Any> = emptyMap()): HttpResponse {
        try {
            val resourceStream = javaClass.getResourceAsStream(pageName) ?: return HttpResponse(404)
            println("Serving $pageName")
            val dataModel = hashMapOf<String, Any>()
            dataModel += baseDataModel
            dataModel["debugMode"] = Config[Debug.enabled]

            if(session != Session.Anonymous) {
                dataModel["auth"] = Auth(session.username, TJDatabase.getPermissions(session.username))
            }
            dataModel["text"] = org.jglrxavpok.tinyjukebox.templating.Text()
            dataModel["Timings"] = org.jglrxavpok.tinyjukebox.templating.Timings()
            dataModel["Network"] = org.jglrxavpok.tinyjukebox.templating.Network()
            dataModel["Security"] = org.jglrxavpok.tinyjukebox.templating.Security()

            val contentType = getMIME(pageName)
            return when {
                pageName.endsWith(".png") -> {
                    StaticResourceResponse(contentType, resourceStream)
                }
                pageName.endsWith(".twig") -> {
                    val template = JtwigTemplate.classpathTemplate(pageName)
                    val model = JtwigModel.newModel(dataModel)
                    val out = ByteArrayOutputStream()
                    template.render(model, out)
                    val result = TextResponse(contentType, String(out.toByteArray()))
                    out.close()
                    result
                }
                else -> {
                    val text = resourceStream.reader().readText()
                    TextResponse(contentType, text)
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Error while serving $pageName", e)
        }
    }

    /**
     * Tries to find the MIME type of a given resource
     */
    private fun getMIME(pageName: String): String {
        val extension = pageName.substringAfterLast(".")
        try {
            return when(extension) {
                "css" -> "text/css"
                "js" -> "text/javascript"
                "png" -> "image/png"
                "html" -> "text/html"

                "eot" -> "application/vnd.ms-fontobject"
                "woff" -> "application/font-woff"
                "woff2" -> "application/font-woff2"
                "ttf" -> "application/x-font-truetype"
                "svg" -> "image/svg+xml"
                "otf" -> "application/x-font-opentype"
                "twig" -> getMIME(pageName.substringBeforeLast("."))

                else -> javaClass.getResource(pageName).openConnection().contentType
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return "text/html"
        }
    }
}