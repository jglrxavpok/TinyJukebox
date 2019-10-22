package org.jglrxavpok.tinyjukebox

/**
 * Thread to simulate a pipe between two processes
 */
class PipingThread(val left: Process, val right: Process): Thread("Piping thread $left | $right") {

    override fun run() {
        val buffer = ByteArray(1024)
        try {
            do {
                // read from first process
                val read = left.inputStream.read(buffer)
                if(read > 0) {
                    // write to second process
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
