package org.jglrxavpok.tinyjukebox.auth

import org.jetbrains.exposed.sql.transactions.transaction
import org.jglrxavpok.tinyjukebox.TJDatabase
import org.jglrxavpok.tinyjukebox.auth
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.PrintWriter

/**
 * Checks authentification of users to perform certain actions
 */
object AuthChecker {

    /**
     * Generates a function to be used in WebActions
     */
    fun checkAuth(callback: ((PrintWriter, BufferedReader, String, String) -> Unit)?): (PrintWriter, Long, BufferedReader, InputStream, Map<String, String>, Map<String, String>, Session) -> Unit {
        return { writer, length, reader, input, attributes, cookies, session ->
            val username = reader.readLine()
            val passwordClear = RSADecode(reader.readLine())

            if(checkAuth(username, passwordClear)) {
                writer.println("yes")
                if(callback != null) {
                    callback(writer, reader, username, passwordClear)
                }
            } else {
                writer.println("no")
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