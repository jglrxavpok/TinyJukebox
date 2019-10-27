package org.jglrxavpok.tinyjukebox.http.controllers

import io.github.magdkudama.krouter.RouteResponse
import org.jglrxavpok.tinyjukebox.http.Controller
import org.jglrxavpok.tinyjukebox.http.HttpInfo
import org.jglrxavpok.tinyjukebox.http.TextResponse
import org.jglrxavpok.tinyjukebox.websocket.QuoteThread

/**
 * Controller responsible of the main index page and the quote API
 */
class IndexController(httpInfo: HttpInfo): Controller(httpInfo) {

    fun index(): RouteResponse {
        return serve("/index.html.twig")
    }

    fun quote(): RouteResponse {
        return TextResponse("text/plain", QuoteThread.currentQuote)
    }
}