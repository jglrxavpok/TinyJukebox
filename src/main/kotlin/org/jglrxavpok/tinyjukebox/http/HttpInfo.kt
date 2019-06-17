package org.jglrxavpok.tinyjukebox.http

import org.jglrxavpok.tinyjukebox.auth.Session
import java.io.BufferedReader
import java.io.InputStream
import java.io.PrintWriter

data class HttpInfo(val writer: PrintWriter, val length: Long, val clientReader: BufferedReader,
                    val clientInput: InputStream, val attributes: Map<String, String>,
                    val cookies: Map<String, String>, val session: Session)
