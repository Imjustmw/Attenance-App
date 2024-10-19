package dev.kwasi.echoservercomplete.models

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.text.Charsets.UTF_8
import kotlin.random.Random

// 'object' makes it a singleton where methods can be called without an instance.
object Encryption {

    // Generate random number within range
    fun rand(min: Int = 1, max: Int = 100000): Int {
        return Random.nextInt(min, max)
    }

    // Hash a string using SHA-256
    private fun hashStrSha256(str: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(str.toByteArray(UTF_8)).joinToString("") { "%02x".format(it) }
    }

    // Generate an AES key from a seed
    fun generateAESKey(seed: String): SecretKeySpec {
        val keyBytes = hashStrSha256(seed).substring(0, 32).toByteArray(UTF_8)
        return SecretKeySpec(keyBytes, "AES")
    }

    // Generate an Initialization Vector (IV) for AES
    fun generateIV(seed: String): IvParameterSpec {
        // Add 0's to makeup 16 byte seed
        val ivSeed = seed.padEnd(16, '0').substring(0,16)
        val ivBytes = ivSeed.toByteArray(UTF_8)
        return IvParameterSpec(ivBytes)
    }

    // Encrypt a message using AES key and IV
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun encryptMessage(plaintext: String, aesKey: SecretKeySpec, aesIv: IvParameterSpec): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)
        val encrypted = cipher.doFinal(plaintext.toByteArray())
        return Base64.Default.encode(encrypted)
    }

    // Decrypt a message using AES key and IV
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun decryptMessage(encryptedText: String, aesKey: SecretKeySpec, aesIv: IvParameterSpec): String {
        val decodedBytes = Base64.Default.decode(encryptedText)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, aesIv)
        return String(cipher.doFinal(decodedBytes))
    }

    // Streamline the processes
    fun encryptWithID(studentId: String, message: String): String {
        val aesKey = generateAESKey(studentId)
        val aesIV = generateIV(studentId)
        val encrypted = encryptMessage(message, aesKey, aesIV)
        return encrypted
    }

    fun decryptWithID(studentId: String, message: String): String {
        val aesKey = generateAESKey(studentId)
        val aesIV = generateIV(studentId)
        val decrypted = decryptMessage(message, aesKey, aesIV)
        return decrypted
    }
}