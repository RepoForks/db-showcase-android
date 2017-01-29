package cz.koto.misak.keystorecompat

import android.annotation.TargetApi
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.*
import java.security.interfaces.RSAPublicKey
import java.util.*
import javax.crypto.*
import javax.crypto.spec.IvParameterSpec

internal object KeystoreCrypto {

    private val LOG_TAG = javaClass.name
    val ORDER_FOR_ENCRYPTED_DATA = ByteOrder.BIG_ENDIAN

    @TargetApi(Build.VERSION_CODES.M)
    fun encryptAES(key: ByteArray, privateKeyEntry: KeyStore.PrivateKeyEntry): ByteArray {
        var iv: ByteArray
        var encryptedKeyForRealm: ByteArray
        try {
            val publicKey = privateKeyEntry.certificate.publicKey

            val inCipher = Cipher.getInstance(KeystoreCompat.rsaCipherMode)
            inCipher.init(Cipher.ENCRYPT_MODE, publicKey)

            encryptedKeyForRealm = inCipher.doFinal(key)
            iv = inCipher.iv

        } catch (e: Exception) {
            Log.e(LOG_TAG, "Encryption2 error", e)
            throw e
        }
        val ivAndEncryptedKey = ByteArray(Integer.SIZE + iv.size + encryptedKeyForRealm.size)

        val buffer = ByteBuffer.wrap(ivAndEncryptedKey)
        buffer.order(ORDER_FOR_ENCRYPTED_DATA)
        buffer.putInt(iv.size)
        buffer.put(iv)
        buffer.put(encryptedKeyForRealm)

        return ivAndEncryptedKey
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun decryptAES(privateKeyEntry: KeyStore.PrivateKeyEntry, ivAndEncryptedKey: ByteArray): ByteArray {

        val buffer = ByteBuffer.wrap(ivAndEncryptedKey)
        buffer.order(ORDER_FOR_ENCRYPTED_DATA)

        val ivLength = buffer.int
        val iv = ByteArray(ivLength)
        val encryptedKey = ByteArray(ivAndEncryptedKey.size - Integer.SIZE - ivLength)

        buffer.get(iv)
        buffer.get(encryptedKey)

        try {
            val cipher = Cipher.getInstance(KeystoreCompat.rsaCipherMode)
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, privateKeyEntry.privateKey, ivSpec)

            return cipher.doFinal(encryptedKey)

        } catch (e: Exception) {
            when (e) {
                is InvalidKeyException -> {
                    throw RuntimeException("key is invalid.")
                }
                is UnrecoverableKeyException -> {
                }
                is NoSuchAlgorithmException -> {
                }
                is BadPaddingException -> {
                }
                is KeyStoreException -> {
                }
                is IllegalBlockSizeException -> {
                }
                is InvalidAlgorithmParameterException -> {
                }
            }
            throw e
        }
    }

    /**
     * Encrypt bytes to Base64 encoded string.
     * For input secret as string use: secret.toByteArray(Charsets.UTF_8)
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    fun encryptRSA(secret: ByteArray, privateKeyEntry: KeyStore.PrivateKeyEntry): String {
        try {
            val publicKey = privateKeyEntry.certificate.publicKey as RSAPublicKey

            /**
             * AndroidOpenSSL works on Lollipop.
             * But on marshmallow it throws: java.security.InvalidKeyException: Need RSA private or public key
             *
             * On Android 6.0 you should Not use "AndroidOpenSSL" for cipher creation,
             * it would fail with "Need RSA private or public key" at cipher init for decryption.
             * Simply use Cipher.getInstance("RSA/ECB/PKCS1Padding")
             */
            val inCipher = Cipher.getInstance(KeystoreCompat.rsaCipherMode/*, "AndroidOpenSSL"*/)
            inCipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val outputStream = ByteArrayOutputStream()
            val cipherOutputStream = CipherOutputStream(outputStream, inCipher)
            cipherOutputStream.write(secret)
            cipherOutputStream.close()

            return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Encryption error", e)
            throw e
        }
    }

    /**
     * Decrypt Base64 encoded encrypted byteArray.
     * For output as string user: String(byteArray, 0, byteArray.size, Charsets.UTF_8)
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    fun decryptRSA(privateKeyEntry: KeyStore.PrivateKeyEntry, encryptedSecret: String): ByteArray {
        try {

            /**
             * AndroidOpenSSL works on Lollipop.
             * But on marshmallow it throws: java.security.InvalidKeyException: Need RSA private or public key
             *
             * On Android 6.0 you should Not use "AndroidOpenSSL" for cipher creation,
             * it would fail with "Need RSA private or public key" at cipher init for decryption.
             * Simply use Cipher.getInstance("RSA/ECB/PKCS1Padding")
             */
            val output = Cipher.getInstance(KeystoreCompat.rsaCipherMode/*, "AndroidOpenSSL"*/)
            output.init(Cipher.DECRYPT_MODE, privateKeyEntry.privateKey)

            val cipherInputStream = CipherInputStream(
                    ByteArrayInputStream(Base64.decode(encryptedSecret, Base64.DEFAULT)), output)
            val values = ArrayList<Byte>()
            var nextByte: Int = -1

            while ({ nextByte = cipherInputStream.read(); nextByte }() != -1) {
                values.add(nextByte.toByte())
            }

            val bytes = ByteArray(values.size)
            for (i in bytes.indices) {
                bytes[i] = values[i]
            }

            return bytes

        } catch (e: Exception) {
            Log.e(LOG_TAG, "decryption error", e)
            throw e
        }
    }
}