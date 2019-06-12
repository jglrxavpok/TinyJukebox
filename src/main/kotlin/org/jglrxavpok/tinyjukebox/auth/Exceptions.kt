package org.jglrxavpok.tinyjukebox.auth

import java.lang.Exception

class AuthenticationException(message: String): Exception(message)

class UserAlreadyExistsException(name: String): Exception("User $name already exists")