package org.jglrxavpok.tinyjukebox

import java.io.InputStream
import java.io.OutputStream

class PipingThread(val left: InputStream, val right: OutputStream): Thread("Piping thread $left | $right") {

    constructor(left: Process, right: Process): this(left.inputStream, right.outputStream)

    override fun run() {
        super.run()
        val buffer = ByteArray(1024)
        try {
            do {
                val read = left.read(buffer)
                //println("$this = $read read")
                if(read > 0) {
                    right.write(buffer, 0, read)
                    right.flush()
                }
            } while(read != -1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
