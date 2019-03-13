package org.jglrxavpok.tinyjukebox

class PipingThread(val left: Process, val right: Process): Thread("Piping thread $left | $right") {


    override fun run() {
        super.run()
        val buffer = ByteArray(1024)
        do {
            val read = left.inputStream.read(buffer)
            if(read > 0)
                right.outputStream.write(buffer, 0, read)
        } while(read != -1)
    }
}
