package org.jglrxavpok.tinyjukebox.websocket

import org.jglrxavpok.tinyjukebox.HttpHandler
import org.jglrxavpok.tinyjukebox.TinyJukebox
import kotlin.random.Random

/**
 * Thread that will update the quote displayed on the webpage
 */
object QuoteThread: Thread("Quote thread") {

    /**
     * List of all quotes
     */
    val quotes by lazy {
        val text = QuoteThread::class.java.getResourceAsStream("/quotes_intech.txt")?.reader()?.readText() ?: "No quotes :c"
        text.split('\n')
    }
    private val quoteRNG = Random(System.currentTimeMillis())
    var currentQuote = quotes.random(quoteRNG)

    // TODO: configurable
    /**
     * Delay between quote changes
     */
    const val quoteDelay = 15000L

    override fun run() {
        while(!interrupted()) {
            currentQuote = quotes.random(quoteRNG)
            TinyJukebox.sendQuote(currentQuote)
            Thread.sleep(quoteDelay)
        }
    }
}