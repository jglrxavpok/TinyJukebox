package org.jglrxavpok.tinyjukebox.auth

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.PrintWriter
import java.lang.IllegalArgumentException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object AuthChecker {
    fun checkAuth(callback: (() -> Unit)?): (PrintWriter, Long, BufferedReader, InputStream, Map<String, String>) -> Unit {
        return { writer, length, reader, input, attributes ->
            val username = reader.readLine()
            val password = reader.readLine()

            val adminAccountFile = File(getOrMkdirAuthFolder(), username)
            if(adminAccountFile.exists()) {
                // ensure the password is correct by using SHA-256 on the given password and checking that it is the same hash that the one stored inside the file
                val sha256 = MessageDigest.getInstance("SHA-256")
                val passwordSha256 = sha256.digest(password.toByteArray(StandardCharsets.UTF_8))
                val passwordHex = passwordSha256.joinToString("") { it.toString(16) }
                val validPassword = adminAccountFile.readText() == passwordHex
                if(validPassword) {
                    writer.println("yes")
                    if(callback != null) {
                        callback()
                    }
                } else {
                    writer.println("no")
                }
            } else {
                writer.println("no")
            }
        }
    }

    fun getOrMkdirAuthFolder(): File {
        val folder = File("./auth")
        if(!folder.exists())
            folder.mkdir()
        return folder
    }

    fun addNewAdminAccount(username: String, password: CharArray) {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val adminAccountFile = File(getOrMkdirAuthFolder(), username)
        if(adminAccountFile.exists()) {
            throw IllegalArgumentException("Account already exists")
        }

        adminAccountFile.writeText(sha256.digest(password.map { it.toByte() }.toByteArray()).joinToString("") { it.toString(16) })
    }
}