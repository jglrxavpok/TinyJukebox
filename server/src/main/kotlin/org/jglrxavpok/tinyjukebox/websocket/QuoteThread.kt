package org.jglrxavpok.tinyjukebox.websocket

import org.jglrxavpok.tinyjukebox.*
import java.io.File
import kotlin.random.Random

/**
 * Thread that will update the quote displayed on the webpage
 */
object QuoteThread: Thread("Quote thread") {

    /**
     * List of all quotes
     */
    val quotes by lazy {
        val text = File(Config[Paths.quotes]).reader().readText()
        text.split('\n')
    }
    private val quoteRNG = Random(System.currentTimeMillis())
    var currentQuote = quotes.random(quoteRNG)

    /**
     * Delay between quote changes
     */
    override fun run() {
        while(!interrupted()) {
            var newQuote: String
            do {
                newQuote = quotes.random(quoteRNG)
            } while(newQuote == currentQuote)
            currentQuote = newQuote
            TinyJukebox.sendQuote(currentQuote)
            Thread.sleep(Config[Timings.quoteDelay])
        }
    }
}