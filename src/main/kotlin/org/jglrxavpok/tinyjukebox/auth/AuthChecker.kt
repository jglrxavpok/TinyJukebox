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
                if(!session.expired) {
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

}