package org.jglrxavpok.tinyjukebox.auth

import org.jglrxavpok.tinyjukebox.Config
import org.jglrxavpok.tinyjukebox.TJDatabase
import org.jglrxavpok.tinyjukebox.Timings
import org.jglrxavpok.tinyjukebox.exceptions.InvalidCredentialsException
import org.jglrxavpok.tinyjukebox.exceptions.InvalidSessionException
import org.jglrxavpok.tinyjukebox.exceptions.UserNotPermittedException
import org.jglrxavpok.tinyjukebox.http.HttpInfo
import java.io.BufferedReader
import java.io.InputStream
import java.io.PrintWriter
import java.util.*

class Session(val id: UUID, val username: String, val expirementDate: Long) {

    val expired: Boolean
        get() = expirementDate < System.currentTimeMillis()

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

        fun login(httpInfo: HttpInfo) {
            with(httpInfo) {
                val username = clientReader.readLine()
                val password = RSADecode(clientReader.readLine())
                login(httpInfo, username, password)
            }
        }

        fun login(httpInfo: HttpInfo, username: String, passwordPlain: String) {
            with(httpInfo) {
                try {
                    val session = Session.open(username, passwordPlain)
                    writer.println("yes")
                    writer.println(session.id.toString())
                } catch (e: InvalidCredentialsException) {
                    e.printStackTrace()
                    writer.println("no")
                }
            }
        }

        fun signup(httpInfo: HttpInfo) {
            with(httpInfo) {
                val username = clientReader.readLine()
                val password = RSADecode(clientReader.readLine())
                signup(httpInfo, username, password)
            }
        }

        fun signup(httpInfo: HttpInfo, username: String, passwordPlain: String) {
            with(httpInfo) {
                try {
                    TJDatabase.newUser(username, passwordPlain, null)
                    writer.println("yes")
                    login(httpInfo, username, passwordPlain)
                } catch (e: UserAlreadyExistsException) {
                    e.printStackTrace()
                    writer.println("no")
                    writer.println("User already exists!")
                }
            }
        }

        fun logout(httpInfo: HttpInfo) {
            with(httpInfo) {
                Session.close(clientReader.readLine())
            }
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