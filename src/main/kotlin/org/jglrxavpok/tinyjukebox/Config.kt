package org.jglrxavpok.tinyjukebox

import java.io.File
import java.io.FileInputStream
import java.util.*

// TODO: Comments for keys
/**
 * Object responsible of the jukebox configuration
 *
 * (Code from 'The Untitled Spaceship by 'jglrxapok' - aka me. Inspired by Konfig)
 */
object Config {

    private val settingsFile = File("./config.cfg")
    private val properties = Properties()

    private val groups = listOf(Paths, Timings, Network, Text, Security, DatabaseConfig)

    fun load() {
        val comments = "TinyJukebox configurations"

        if( ! settingsFile.exists()) {
            settingsFile.createNewFile()

            // load defaults
            groups.forEach { it.load(properties) }
            properties.store(settingsFile.outputStream(), comments)
        }

        FileInputStream(settingsFile).use {
            properties.load(it)
        }

        groups.forEach { it.load(properties) }

        val output = settingsFile.outputStream()
        groups.forEach { it.save(properties) }
        properties.store(output, comments)
        output.flush()
        output.close()
    }

    operator fun <T> set(key: Key<T>, value: T) {
        key.currentValue = value
    }

    operator fun <T> get(key: Key<T>): T {
        return key.currentValue
    }

    fun getFromProperties(varName: String): String {
        return properties[varName].toString()
    }
}

object Text: KeyGroup() {
    val title = StringKey("TinyJukebox")
}

object Paths: KeyGroup() {
    val quotes = StringKey("./src/main/resources/quotes.txt")
}

object DatabaseConfig: KeyGroup() {
    val url = StringKey("jdbc:sqlite:db.bin")
    val driver = StringKey("org.sqlite.JDBC")
}

object Timings: KeyGroup() {
    val quoteDelay = LongKey(15000L)
    val quoteFadeIn = LongKey(750L)
    val quoteFadeOut = LongKey(750L)
    val sessionExpiration = LongKey(30*60*1000L) // 30s by default
}

object Network: KeyGroup() {
    val httpsPort = IntKey(8080)
    val websocketPort = IntKey(8887)
}

object Security: KeyGroup() {
    val httpsCertificate = StringKey("./httpsCertificate.key")
    val httpsCertificatePassword = StringKey("PLEASE CHANGE THIS")
    val wssCertificate = StringKey("./wssCertificate.key")
    val wssCertificatePassword = StringKey("PLEASE CHANGE THIS")
    val rsaKeystore = StringKey("./rsa.key")
    val rsaUser = StringKey("PLEASE CHANGE THIS")
    val rsaPassword = StringKey("PLEASE CHANGE THIS")
    val useSSL = BooleanKey(true)
}

abstract class Key<T> {
    internal abstract var currentValue: T
    abstract fun convertToString(): String
    abstract fun convertFromString(value: String)
    abstract fun setToDefault()
}

class StringKey(val default: String = "") : Key<String>() {
    override var currentValue: String = default

    override fun convertToString(): String {
        return currentValue
    }

    override fun convertFromString(value: String) {
        currentValue = value
    }

    override fun setToDefault() {
        this.currentValue = default
    }

}

class LongKey(val default: Long = 0): Key<Long>() {
    override var currentValue = default

    override fun convertToString() = currentValue.toString()

    override fun convertFromString(value: String) {
        currentValue = value.toLong()
    }

    override fun setToDefault() {
        currentValue = default
    }
}

class IntKey(val default: Int = 0): Key<Int>() {
    override var currentValue = default

    override fun convertToString() = currentValue.toString()

    override fun convertFromString(value: String) {
        currentValue = value.toInt()
    }

    override fun setToDefault() {
        currentValue = default
    }
}


class FloatKey(val default: Float = 0f): Key<Float>() {
    override var currentValue = default

    override fun convertToString() = currentValue.toString()

    override fun convertFromString(value: String) {
        currentValue = value.toFloat()
    }

    override fun setToDefault() {
        currentValue = default
    }
}

class BooleanKey(val default: Boolean = false): Key<Boolean>() {
    override var currentValue = default

    override fun convertToString() = currentValue.toString()

    override fun convertFromString(value: String) {
        currentValue = value.toLowerCase() == "true"
    }

    override fun setToDefault() {
        currentValue = default
    }
}

open class KeyGroup {

    private fun listProperties() = this.javaClass.declaredFields.filter { Key::class.java.isAssignableFrom(it.type) }.map {
        it.isAccessible = true
        val r = it.get(this@KeyGroup) as Key<*> to it.name
        it.isAccessible = false
        r
    }

    fun load(props: Properties, groupName: String = this.javaClass.simpleName) {
        for((key, name) in listProperties()) {
            val keyName = "$groupName.$name"
            key.setToDefault()
            if(props.containsKey(keyName))
                key.convertFromString(props.getProperty(keyName))
        }
    }

    fun save(props: Properties, groupName: String = this.javaClass.simpleName) {
        for((key, name) in listProperties()) {
            val keyName = "$groupName.$name"
            props.setProperty(keyName, key.convertToString())
        }
    }
}
