package org.jglrxavpok.tinyjukebox.auth

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.PrintWriter
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
            val passwordHash = reader.readLine()

            val adminAccountFile = File(getOrMkdirAuthFolder(), username)
            if(adminAccountFile.exists() && !adminAccountFile.isDirectory) {
                // ensure the password is correct by using SHA-256 on the given password and checking that it is the same hash that the one stored inside the file
                val validPassword = adminAccountFile.readText() == passwordHash
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

    @ExperimentalUnsignedTypes // just to make it clear that the experimental unsigned types are used
    fun ByteArray.toHexString() = asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }

    /**
     * Adds a new admin account to the authenfication system
     */
    fun addNewAdminAccount(username: String, password: CharArray) {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val adminAccountFile = File(getOrMkdirAuthFolder(), username)
        if(adminAccountFile.exists()) {
            throw IllegalArgumentException("Account already exists")
        }

        sha256.update(String(password).toByteArray(Charsets.UTF_8))
        // convert to hex
        val digest = sha256.digest()
        val passwordHash = digest.toHexString()
        adminAccountFile.writeText(passwordHash)
    }
}