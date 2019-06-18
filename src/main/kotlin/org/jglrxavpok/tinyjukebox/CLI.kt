package org.jglrxavpok.tinyjukebox

import org.jglrxavpok.tinyjukebox.templating.TJDatabase
import java.lang.IllegalArgumentException
import java.util.*
import javax.swing.JOptionPane
import javax.swing.JPasswordField

private val scanner by lazy { Scanner(System.`in`) }

fun main(args: Array<String>) {
    Config.load()
    println("Connecting to database...")
    TJDatabase.init()
    do {
        var stop = false
        println("Command: ")
        val command = scanner.nextLine()
        when(command) {
            "stop", "exit", "q" -> {
                stop = true
            }
            "newaccount" -> {
                newaccount()
            }
            "newadmin" -> {
                newadmin()
            }
            else -> println("Not understood")
        }
    } while(!stop)
}

fun newadmin() {
    val username = prompt("Username", password = false)
    TJDatabase.forceAddAdmin(String(username))
}

fun newaccount() {
    val username = prompt("Username", password = false)
    val password = prompt("Password", password = true)
    TJDatabase.newUser(String(username), String(password), null)
}

private fun prompt(message: String, password: Boolean): CharArray {
    return if(password) {
        val console = System.console()
        if(console == null) {
            val passwordField = JPasswordField()
            if(JOptionPane.showConfirmDialog( null, passwordField, message, JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE ) == JOptionPane.OK_OPTION) {
                passwordField.password
            } else {
                throw IllegalArgumentException("No password!")
            }
        } else {
            console.readPassword("$message: ")
        }
    } else {
        println("$message: ")
        scanner.nextLine().toCharArray()
    }
}

private fun Array<String>.getOrError(index: Int): String {
    if(size <= index)
        throw IndexOutOfBoundsException("index: $index, size: $size")
    return this[index]
}
