package org.jglrxavpok.tinyjukebox.auth

import org.jglrxavpok.tinyjukebox.Config
import org.jglrxavpok.tinyjukebox.TJDatabase
import org.jglrxavpok.tinyjukebox.Timings
import org.jglrxavpok.tinyjukebox.exceptions.InvalidCredentialsException
import org.jglrxavpok.tinyjukebox.exceptions.InvalidSessionException
import org.jglrxavpok.tinyjukebox.exceptions.UserNotPermittedException
import java.io.BufferedReader
import java.io.InputStream
import java.io.PrintWriter
import java.util.*

class Session(val id: UUID, val username: String, val expirementDate: Long) {

    companion object {
        val Anonymous = Session(UUID.fromString("00000000-0000-0000-0000-000000000000"), "<ANONYMOUS>", Long.MAX_VALUE)

        private val idMap = HashMap<String, Session>()

        fun load(sessionId: String): Session {
            // TODO: hashing?
            val session = idMap[sessionId] ?: throw InvalidSessionException(sessionId)
            if(session.isExpired()) {
                close(sessionId)
                throw InvalidSessionException(sessionId)
            }

            return session
        }

        fun open(username: String, passwordHash: String): Session {
            if(AuthChecker.checkAuth(username, passwordHash)) {
                // remove any existing session for the given username
                idMap.filter { it.value.username == username }.forEach { idMap.remove(it.key) }

                val sessionID = generateSessionID()
                val session = Session(sessionID, username, System.currentTimeMillis()+Config[Timings.sessionExpiration])
                idMap[sessionID.toString()] = session
                return session
            } else {
                throw InvalidCredentialsException()
            }
        }

        fun close(sessionId: String) {
            idMap.remove(sessionId)
        }

        private fun generateSessionID(): UUID {
            return UUID.randomUUID()
        }

        fun login(writer: PrintWriter, clientReader: BufferedReader, username: String, passwordHash: String) {
            try {
                val session = Session.open(username, passwordHash)
                writer.println(session.id.toString())
            } catch (e: InvalidCredentialsException) {
                e.printStackTrace()
                writer.println("no")
            }
        }

        fun signup(writer: PrintWriter, length: Long, clientReader: BufferedReader, clientInput: InputStream, attributes: Map<String, String>, cookies: Map<String, String>, session: Session) {
            try {
                val username = clientReader.readLine()
                val password = RSADecode(clientReader.readLine())
                TJDatabase.newUser(username, password, null)
                writer.println("yes")
                login(writer, clientReader, username, password)
            } catch (e: UserAlreadyExistsException) {
                e.printStackTrace()
                writer.println("no")
                writer.println("User already exists!")
            }
        }

        fun logout(writer: PrintWriter, length: Long, clientReader: BufferedReader, clientInput: InputStream, attributes: Map<String, String>, cookies: Map<String, String>, session: Session) {
            Session.close(clientReader.readLine())
        }

        fun exists(sessionId: String): Boolean {
            if(sessionId in idMap) {
                val session = idMap[sessionId]!!
                if(session.isExpired())
                    return false
                return session != Anonymous
            }
            return false
        }
    }

    private fun isExpired(): Boolean {
        return System.currentTimeMillis() >= expirementDate
    }

    fun checkPermissions(permissions: List<Permissions>) {
        val userPermissions = TJDatabase.getPermissions(username)
        if( ! userPermissions.containsAll(permissions)) {
            val diff = arrayListOf<Permissions>()
            diff += permissions
            diff -= userPermissions
            throw UserNotPermittedException(diff)
        }
    }

}