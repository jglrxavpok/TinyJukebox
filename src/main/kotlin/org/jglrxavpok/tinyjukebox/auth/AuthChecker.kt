package org.jglrxavpok.tinyjukebox.auth

import org.jetbrains.exposed.sql.transactions.transaction
import org.jglrxavpok.tinyjukebox.templating.auth
import org.jglrxavpok.tinyjukebox.http.HttpInfo
import java.io.BufferedReader
import java.io.File
import java.io.PrintWriter

/**
 * Checks authentification of users to perform certain actions
 */
object AuthChecker {

    /**
     * Generates a function to be used in WebActions
     */
    fun checkAuth(callback: ((PrintWriter, BufferedReader, Session) -> Unit)?): (HttpInfo) -> Unit {
        return { httpInfo ->
            with(httpInfo) {
                if(session.expired) {
                    writer.println("yes")
                    if(callback != null) {
                        callback(writer, clientReader, session)
                    }
                } else {
                    writer.println("no")
                }
            }
        }
    }

    /**
     * Checks that a given username exists and that the given password is the correct one
     */
    fun checkAuth(username: String, passwordClear: String): Boolean {
        return transaction {
            auth(username, passwordClear)
        }
    }

    /**
     * Gets the auth folder or generates it if needed
     */
    fun getOrMkdirAuthFolder(): File {
        val folder = File("./auth")
        if(!folder.exists())
            folder.mkdir()
        return folder
    }

    @ExperimentalUnsignedTypes // just to make it clear that the experimental unsigned types are used
    fun ByteArray.toHexString() = asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }
}