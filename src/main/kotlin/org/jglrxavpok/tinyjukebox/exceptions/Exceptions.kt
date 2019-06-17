package org.jglrxavpok.tinyjukebox.exceptions

import org.jglrxavpok.tinyjukebox.auth.Permissions
import java.lang.Exception

class InvalidSessionException(sessionId: String): Exception(sessionId)
class InvalidCredentialsException(): Exception()
class UserNotPermittedException(val missingPermissions: List<Permissions>): Exception(
    missingPermissions.joinToString(", ") { it.name }
)