package org.jglrxavpok.tinyjukebox.exceptions

import java.lang.Exception

class InvalidSessionException(sessionId: String): Exception(sessionId)
class InvalidCredentialsException(): Exception()