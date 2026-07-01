package netlib.util

import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

object PasswordUtil {
    private var publicKey: PublicKey? = null
    private var rsaOAEPEncrypt: Cipher? = null
    private val publicKeyPem = ("-----BEGIN PUBLIC KEY-----\n" +
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC6EvuuowDR5OwyyITD2uHoHR+P\n" +
            "vXGXCqsXEsQmpVavevyiF6HN1eV5Ce6yvIHRAjOY2UKbePFyftZkfNzLMjVgJpN1\n" +
            "fjHwcn4wxBhJasWEFLJilX7Wft56KfVkT+E/pSU9+a0XJhtrYG6e2CEeu46MpmqU\n" +
            "Qbf1fgqr7CnZJUmBZwIDAQAB\n" +
            "-----END PUBLIC KEY-----\n").toByteArray()

    @Throws(Exception::class)
    fun init() {
        // configure
        publicKey = parsePublicKey(publicKeyPem)
        val spec = OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
            PSource.PSpecified("aibabel".toByteArray())
        )
        rsaOAEPEncrypt = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsaOAEPEncrypt?.init(Cipher.ENCRYPT_MODE, publicKey, spec)
    }

    @Throws(Exception::class)
    fun generatePassword(raw: ByteArray): String {
        val cipher = rsaOAEPEncrypt ?: throw IllegalStateException("PasswordUtils not initialized")
        val outputSize = cipher.getOutputSize(0)
        val chunkSize = outputSize - 2 * 32 - 2
        val numChunks = if (raw.size % chunkSize != 0) raw.size / chunkSize + 1 else raw.size / chunkSize
        val inBuf = ByteBuffer.wrap(raw)
        val outBuf = ByteBuffer.allocate(outputSize * numChunks)
        println("numChunks: $numChunks")
        for (i in 0 until numChunks) {
            val limit = if (chunkSize * (i + 1) <= inBuf.capacity()) chunkSize * (i + 1) else inBuf.capacity()
            inBuf.limit(limit)
            cipher.doFinal(inBuf, outBuf)
        }
        return Base64.encodeToString(outBuf.array(), Base64.NO_WRAP)
    }

    fun generatePassword(id: String, date: Date): String {
        val raw = ("{\"id\":\"$id\",\"time\":${date.time / 1000}}").toByteArray()
        return try {
            generatePassword(raw)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @Throws(Exception::class)
    private fun parsePublicKey(raw: ByteArray): RSAPublicKey {
        val key = String(raw).replace("-----BEGIN PUBLIC KEY-----", "")
            .replace(System.lineSeparator(), "")
            .replace("-----END PUBLIC KEY-----", "")
        val encoded = Base64.decode(key, Base64.DEFAULT)
        val keyFactory = KeyFactory.getInstance("RSA")
        val keySpec = X509EncodedKeySpec(encoded)
        return keyFactory.generatePublic(keySpec) as RSAPublicKey
    }
}
