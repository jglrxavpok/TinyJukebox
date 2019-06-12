package org.jglrxavpok.tinyjukebox

import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jglrxavpok.tinyjukebox.auth.AuthenticationException
import org.jglrxavpok.tinyjukebox.auth.UserAlreadyExistsException
import org.joda.time.DateTime
import org.mindrot.jbcrypt.BCrypt
import java.lang.IllegalArgumentException
import java.sql.Connection

/**
 * Contains all methods to interact with the database
 */
object TJDatabase {

    /**
     * Database object
     */
    private val db by lazy {
        // In file
        Database.connect(Config[DatabaseConfig.url], Config[DatabaseConfig.driver])
    }

    /**
     * Table of Users
     */
    object Users: IntIdTable() {
        val name = varchar("name", 100).primaryKey()
        val hashedPassword = varchar("hash", 200)
        val salt = varchar("salt", 200)
        val avatarURL = varchar("url", 2083).nullable()
        val timeCreated = datetime("creation")
    }

    /**
     * Table of admins
     */
    object Admins: IntIdTable() {
        val name = varchar("name", 100).primaryKey()
    }

    /**
     * Add a new user into the database
     * @param username Name of the new user
     * @param password Password of the new user
     * @param userAvatarURL The URL of the user avatar (or null if none)
     */
    fun newUser(username: String, password: String, userAvatarURL: String?) {
        transaction {
            if(checkUserExists(username)) {
                throw UserAlreadyExistsException(username)
            }

            Users.insert {
                it[name] = username
                val gensalt= BCrypt.gensalt()
                it[salt] = gensalt
                it[hashedPassword] = BCrypt.hashpw(password, gensalt)
                it[avatarURL] = userAvatarURL
                it[timeCreated] = DateTime.now()
            }
        }
    }

    fun forceAddAdmin(username: String) {
        transaction {
            val exists = checkUserExists(username)

            if(!exists) {
                throw IllegalArgumentException("User $username does not exists")
            }

            Admins.insert {
                it[name] = username
            }
        }
    }

    /**
     * Adds an admin inside the TinyJukebox database. Only admins can do this
     * @param adminName The admin to add
     * @param username  Username of the user trying to add the new admin
     * @param userPassword  Password of the user trying to add the new admin (plaintext)
     */
    fun addAdmin(adminName: String, username: String, userPassword: String) {
        transaction {
            val isValidAdmin = auth(username, userPassword)
            val exists = checkUserExists(username)

            if(!isValidAdmin) {
                throw AuthenticationException("User $username is not an admin")
            }
            if(!exists) {
                throw IllegalArgumentException("User $adminName does not exists")
            }

            Admins.insert {
                it[name] = adminName
            }
        }
    }

    /**
     * Removes an admin inside the TinyJukebox database. Only admins can do this
     * @param adminName The admin to remove
     * @param username  Username of the user trying to remove the other admin
     * @param userPassword  Password of the user trying to remove the other admin (plaintext)
     */
    fun removeAdmin(adminName: String, username: String, userPassword: String) {
        transaction {
            val isValidAdmin = auth(username, userPassword)
            val exists = checkUserExists(username)

            if(!isValidAdmin) {
                throw AuthenticationException("User $username is not an admin")
            }
            if(!exists) {
                throw IllegalArgumentException("User $adminName does not exists")
            }

            Admins.deleteWhere {
                Admins.name eq adminName
            }
        }
    }

    /**
     * Create the local database if needed
     */
    fun init() {
        db // connect to the database
        // set isolation level (SQLite requires it)
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction {
            addLogger(StdOutSqlLogger)

            SchemaUtils.createMissingTablesAndColumns(Users, Admins)
        }
    }
}

/**
 * Checks against the database that the given user is an admin
 */
fun Transaction.isAdmin(username: String): Boolean {
    return ! TJDatabase.Admins.select {
        TJDatabase.Admins.name eq username
    }.empty()
}

/**
 * Authenticates the user with its username and password (plaintext)
 */
fun Transaction.auth(username: String, password: String): Boolean {
    val hashPassword = TJDatabase.Users.select {
        TJDatabase.Users.name eq username
    }.first()[TJDatabase.Users.hashedPassword]
    return BCrypt.checkpw(password, hashPassword)
}

/**
 * Checks that the given user exists
 */
fun Transaction.checkUserExists(username: String): Boolean {
    return ! TJDatabase.Users.select {
        TJDatabase.Users.name eq username
    }.empty()
}