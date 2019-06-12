package org.jglrxavpok.tinyjukebox.auth

import org.jglrxavpok.tinyjukebox.Config
import org.jglrxavpok.tinyjukebox.Security
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.Base64.getEncoder
import javax.crypto.Cipher
import kotlin.text.Charsets.UTF_8
import java.util.Base64.getDecoder




private lateinit var keypair: KeyPair
val RSAPublicKey get() = keypair.public

fun RSALoadKeyOrCreate(path: String) {
    val file = File(Config[Security.rsaKeystore])
    if(file.exists()) {
        // Read Public Key.
        val filePublicKey = File("$path.public")
        var fis = FileInputStream(filePublicKey)
        val encodedPublicKey = ByteArray(filePublicKey.length().toInt())
        fis.read(encodedPublicKey)
        fis.close()

        // Read Private Key.
        val filePrivateKey = File("$path.private")
        fis = FileInputStream(filePrivateKey)
        val encodedPrivateKey = ByteArray(filePrivateKey.length().toInt())
        fis.read(encodedPrivateKey)
        fis.close()

        // Generate KeyPair.
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKeySpec = X509EncodedKeySpec(
            encodedPublicKey
        )
        val publicKey = keyFactory.generatePublic(publicKeySpec)

        val privateKeySpec = PKCS8EncodedKeySpec(
            encodedPrivateKey
        )
        val privateKey = keyFactory.generatePrivate(privateKeySpec)


        keypair = KeyPair(publicKey, privateKey)
    } else {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        keypair = generator.generateKeyPair()

        val publicKey = keypair.public
        val privateKey = keypair.private
        val x509EncodedKeySpec = X509EncodedKeySpec(
            publicKey.encoded
        )
        var fos = FileOutputStream("$path.public")
        fos.write(x509EncodedKeySpec.encoded)
        fos.close()

        // Store Private Key.
        val pkcs8EncodedKeySpec = PKCS8EncodedKeySpec(
            privateKey.encoded
        )
        fos = FileOutputStream("$path.private")
        fos.write(pkcs8EncodedKeySpec.encoded)
        fos.close()
    }
}

fun RSAEncode(plainText: String): String {
    val encryptCipher = Cipher.getInstance("RSA")
    encryptCipher.init(Cipher.ENCRYPT_MODE, keypair.public)

    val cipherText = encryptCipher.doFinal(plainText.toByteArray(UTF_8))

    return Base64.getEncoder().encodeToString(cipherText)
}

fun RSADecode(plainText: String): String {
    val bytes = Base64.getDecoder().decode(plainText)

    val decriptCipher = Cipher.getInstance("RSA")
    decriptCipher.init(Cipher.DECRYPT_MODE, keypair.private)

    return String(decriptCipher.doFinal(bytes), UTF_8)
}