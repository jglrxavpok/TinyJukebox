package org.jglrxavpok.tinyjukebox.auth

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.PrintWriter
import java.lang.IllegalArgumentException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Checks authentification of users to perform certain actions
 */
object AuthChecker {

    /**
     * Generates a function to be used in WebActions
     */
    fun checkAuth(callback: ((PrintWriter, BufferedReader) -> Unit)?): (PrintWriter, Long, BufferedReader, InputStream, Map<String, String>) -> Unit {
        return { writer, length, reader, input, attributes ->
            val username = reader.readLine()
            val password = reader.readLine()

            val adminAccountFile = File(getOrMkdirAuthFolder(), username)
            if(adminAccountFile.exists() && !adminAccountFile.isDirectory) {
                // ensure the password is correct by using SHA-256 on the given password and checking that it is the same hash that the one stored inside the file
                val sha256 = MessageDigest.getInstance("SHA-256")
                val passwordSha256 = sha256.digest(password.toByteArray(StandardCharsets.UTF_8))
                val passwordHex = passwordSha256.joinToString("") { it.toString(16) }
                val validPassword = adminAccountFile.readText() == passwordHex
                if(validPassword) {
                    writer.println("yes")
                    if(callback != null) {
                        callback(writer, reader)
                    }
                } else {
                    writer.println("no")
                    println("Invalid credentials for $username")
                }
            } else {
                writer.println("no")
                println("Invalid credentials for $username")
            }
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

    /**
     * Adds a new admin account to the authenfication system
     */
    fun addNewAdminAccount(username: String, password: CharArray) {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val adminAccountFile = File(getOrMkdirAuthFolder(), username)
        if(adminAccountFile.exists()) {
            throw IllegalArgumentException("Account already exists")
        }

        // convert to hex
        adminAccountFile.writeText(sha256.digest(password.map { it.toByte() }.toByteArray()).joinToString("") { it.toString(16) })
    }
}