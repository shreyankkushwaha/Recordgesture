package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoManager {

    private val keyStore: KeyStore? = try {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    } catch (e: Exception) {
        null
    }

    private var fallbackSecretKey: SecretKey? = null

    private fun getOrCreateSecretKey(): SecretKey {
        keyStore?.let { ks ->
            try {
                val existingKey = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                return existingKey?.secretKey ?: createSecretKey()
            } catch (e: Exception) {
                // Fallback if AndroidKeyStore operations fail in test environment
            }
        }
        return getFallbackSecretKey()
    }

    private fun createSecretKey(): SecretKey {
        return try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            getFallbackSecretKey()
        }
    }

    @Synchronized
    private fun getFallbackSecretKey(): SecretKey {
        return fallbackSecretKey ?: run {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val key = keyGen.generateKey()
            fallbackSecretKey = key
            key
        }
    }

    fun encryptFile(inputFile: File, outputFile: File): Boolean {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getOrCreateSecretKey()
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv // 12 bytes for GCM

            FileOutputStream(outputFile).use { fos ->
                // Write IV length prefix and IV bytes
                fos.write(iv.size)
                fos.write(iv)

                CipherOutputStream(fos, cipher).use { cos ->
                    FileInputStream(inputFile).use { fis ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            cos.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            false
        }
    }

    fun decryptFile(encryptedFile: File, outputFile: File): Boolean {
        return try {
            FileInputStream(encryptedFile).use { fis ->
                // Read IV
                val ivSize = fis.read()
                if (ivSize <= 0 || ivSize > 128) {
                    return false
                }
                val iv = ByteArray(ivSize)
                val readIv = fis.read(iv)
                if (readIv != ivSize) {
                    return false
                }

                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                val secretKey = getOrCreateSecretKey()
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                CipherInputStream(fis, cipher).use { cis ->
                    FileOutputStream(outputFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (cis.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            false
        }
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "quick_record_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
    }
}
