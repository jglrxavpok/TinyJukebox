package org.jglrxavpok.tinyjukebox.http

import html.htmlErrorCodeToName
import io.github.magdkudama.krouter.RouteResponse
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter

/**
 * Route Response that is a valid HTTP response
 */
open class HttpResponse(val errorCode: Int, val contentType: String = "text/html", vararg val headerParameters: String): RouteResponse {

    open fun write(outputStream: OutputStream, writer: PrintWriter) {
        writer.println("HTTP/1.1 $errorCode ${htmlErrorCodeToName[errorCode]}")
        writer.println("Content-Type: $contentType; charset=utf-8")
        for(param in headerParameters) {
            writer.println(param)
        }
        writer.println("")
    }

    override fun toString(): String {
        val baos = ByteArrayOutputStream()
        val writer = PrintWriter(baos)
        this.write(baos, writer)
        writer.close()
        return String(baos.toByteArray())
    }
}

/**
 * Route Response that is a valid HTTP response, with the body containing the static resource
 */
class StaticResourceResponse(mime: String, val inputStream: InputStream): HttpResponse(200, mime) {

    override fun write(outputStream: OutputStream, writer: PrintWriter) {
        super.write(outputStream, writer)
        writer.flush()
        outputStream.write(inputStream.readBytes())
        outputStream.flush()
    }
}

/**
 * Route Response that is a valid HTTP response, with the body containing the given text
 */
class TextResponse(mime: String, val text: String, errorCode: Int = 200): HttpResponse(errorCode, mime) {
    override fun write(outputStream: OutputStream, writer: PrintWriter) {
        super.write(outputStream, writer)
        writer.println(text)
    }
}