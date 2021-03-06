package org.jglrxavpok.tinyjukebox.templating

import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jglrxavpok.tinyjukebox.Config
import org.jglrxavpok.tinyjukebox.DatabaseConfig
import org.jglrxavpok.tinyjukebox.player.Music
import org.jglrxavpok.tinyjukebox.auth.AuthenticationException
import org.jglrxavpok.tinyjukebox.auth.Session
import org.jglrxavpok.tinyjukebox.auth.Permissions as UserPermissions
import org.jglrxavpok.tinyjukebox.auth.UserAlreadyExistsException
import org.jglrxavpok.tinyjukebox.player.FileSource
import org.jglrxavpok.tinyjukebox.player.YoutubeSource
import org.joda.time.DateTime
import org.mindrot.jbcrypt.BCrypt
import java.io.File
import java.sql.Connection

/**
 * Contains all methods to interact with the database
 */
object TJDatabase {

    private val PermissionList = UserPermissions.values().map(UserPermissions::name)
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
        //val index = integer("index").uniqueIndex().autoIncrement()
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
     //   val index = integer("index").uniqueIndex().autoIncrement()
        val name = varchar("name", 100).primaryKey()
    }

    object Musics: IntIdTable() {
     //   val index = integer("index").uniqueIndex().autoIncrement()
        val name = varchar("name", 100).primaryKey()
        val musicSource = varchar("source", 100).primaryKey()
        val location = varchar("location", 2083).default("")
        val length = long("length").default(0)
        val timesPlayedTotal = integer("timesPlayed").default(0)
        val timesSkippedTotal = integer("timesSkipped").default(0)
    }

    object Favorites: IntIdTable() {
     //   val index = integer("index").uniqueIndex().autoIncrement()
        val user = varchar("user", 100).primaryKey() references TJDatabase.Users.name
        val music = varchar("music", 100).primaryKey() references TJDatabase.Musics.name
        val timesPlayed = integer("timesPlayed").default(0)
    }

    object Permissions: Table() {
        val user = varchar("user", 100).primaryKey() references TJDatabase.Users.name
        val permission = varchar("permission", 100).primaryKey()

        init {
            permission.check {
                permission inList PermissionList
            }
        }
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

            TJDatabase.Users.insert {
                it[name] = username
                val gensalt= BCrypt.gensalt()
                it[salt] = gensalt
                it[hashedPassword] = BCrypt.hashpw(password, gensalt)
                it[avatarURL] = userAvatarURL
                it[timeCreated] = DateTime.now()
            }

            TJDatabase.Permissions.insert {
                it[user] = username
                it[permission] = UserPermissions.Upload.name
            }
        }
    }

    fun forceAddAdmin(username: String) {
        transaction {
            val exists = checkUserExists(username)

            if(!exists) {
                throw IllegalArgumentException("User $username does not exists")
            }

            TJDatabase.Admins.insert {
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

            TJDatabase.Admins.insert {
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

            TJDatabase.Admins.deleteWhere {
                TJDatabase.Admins.name eq adminName
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

            SchemaUtils.createMissingTablesAndColumns(
                Users,
                Admins,
                Musics,
                Favorites,
                Permissions
            )
        }
    }

    fun onMusicUpload(session: Session, music: Music) {
        transaction {
            if(TJDatabase.Musics.select { TJDatabase.Musics.name eq music.name }.empty()) {
                TJDatabase.Musics.insertIgnore {
                    it[name] = music.name
                    it[timesPlayedTotal] = 0
                    it[timesSkippedTotal] = 0
                    it[length] = music.duration
                    it[location] = music.source.location
                    it[musicSource] = music.source.javaClass.simpleName
                }
            }
            TJDatabase.Musics.update({ TJDatabase.Musics.name eq music.name }) {
                with(SqlExpressionBuilder) {
                    it.update(timesPlayedTotal, timesPlayedTotal +1)
                }
            }
            if(TJDatabase.Favorites.select { (TJDatabase.Favorites.music eq music.name) and (TJDatabase.Favorites.user eq session.username) }.empty()) {
                TJDatabase.Favorites.insertIgnore {
                    it[user] = session.username
                    it[TJDatabase.Favorites.music] = music.name
                    it[timesPlayed] = 0
                }
            }
            TJDatabase.Favorites.update({ (TJDatabase.Favorites.music eq music.name) and (TJDatabase.Favorites.user eq session.username) }) {
                with(SqlExpressionBuilder) {
                    it.update(timesPlayed, timesPlayed +1)
                }
            }
        }
    }

    fun getPermissions(username: String): List<UserPermissions> {
        return transaction {
            TJDatabase.Permissions.select { TJDatabase.Permissions.user eq username }.map { UserPermissions.valueOf(it[TJDatabase.Permissions.permission]) }
        }
    }

    fun onMusicSkip(name: String) {
        transaction {
            TJDatabase.Musics.update({ TJDatabase.Musics.name eq name }) {
                with(SqlExpressionBuilder) {
                    it.update(timesSkippedTotal, timesSkippedTotal + 1)
                }
            }
        }
    }

    fun getSavedMusic(music: String): Music {
        return transaction {
            val row = TJDatabase.Musics.select { TJDatabase.Musics.name eq music }.first()
            val source = row[TJDatabase.Musics.musicSource]
            val location = row[TJDatabase.Musics.location]
            val musicSource = when(source) {
                "YoutubeSource" -> YoutubeSource(location)
                "FileSource" -> FileSource(File(location))
                else -> {
                    throw IllegalArgumentException(music)
                }
            }
            Music(musicSource)
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
    }.firstOrNull()?.get(TJDatabase.Users.hashedPassword) ?: return false
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

/**
 * Checks that the given user exists
 */
fun Transaction.checkMusicExists(musicName: String): Boolean {
    return ! TJDatabase.Musics.select {
        TJDatabase.Musics.name eq musicName
    }.empty()
}

/**
 * Checks that the given user has the given permission
 */
fun Transaction.checkPermission(username: String, permission: String): Boolean {
    return ! TJDatabase.Permissions.select {
        (TJDatabase.Permissions.user eq username) and (TJDatabase.Permissions.permission eq permission)
    }.empty()
}

/**
 * Grants the given permission to a user
 */
fun Transaction.grantPermission(username: String, permission: String) {
    TJDatabase.Permissions.insertIgnore {
        it[user] = username
        it[TJDatabase.Permissions.permission] = permission
    }
}