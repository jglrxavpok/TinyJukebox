package org.jglrxavpok.tinyjukebox

import java.io.InputStream
import java.io.OutputStream

class PipingThread(val left: Process, val right: Process): Thread("Piping thread $left | $right") {

    override fun run() {
        super.run()
        val buffer = ByteArray(1024)
        try {
            do {
                val read = left.inputStream.read(buffer)
                if(read > 0) {
                    right.outputStream.write(buffer, 0, read)
                    right.outputStream.flush()
                }
            } while(read != -1)
            right.outputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
