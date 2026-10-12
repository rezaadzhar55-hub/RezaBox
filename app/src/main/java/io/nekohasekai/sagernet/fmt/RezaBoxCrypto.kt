package io.nekohasekai.sagernet.fmt

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * RezaBox enkripsi export profil.
 * Format file: rbx://<b64url(json)>
 * json = {"mode":..., "salt":..., "iv":..., "data":...}
 * Kunci = SHA-256(kunci user + salt). AES-256-GCM.
 */
object RezaBoxCrypto {

    fun exportProfile(json: String, mode: String, userKey: String?, sshPassword: String?, payload: String?): String {
        val keySource = when (mode) {
            "SSH_ONLY" -> sshPassword ?: ""
            "PAYLOAD_ONLY" -> userKey ?: ""
            "SSH_PAYLOAD" -> ((sshPassword ?: "") + (userKey ?: ""))
            else -> "" // NONE: tetap ada envelope, key = konstanta
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = sha256(keySource.toByteArray(Charsets.UTF_8) + salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val meta = org.json.JSONObject()
            .put("mode", mode)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(ct, Base64.NO_WRAP))
        return "rbx://" + Base64.encodeToString(
            meta.toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
    }

    fun decrypt(link: String, userKey: String?, sshPassword: String?, payload: String?): String {
        val raw = link.removePrefix("rbx://")
        val meta = org.json.JSONObject(
            String(Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        )
        val mode = meta.getString("mode")
        val salt = Base64.decode(meta.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(meta.getString("iv"), Base64.NO_WRAP)
        val ct = Base64.decode(meta.getString("data"), Base64.NO_WRAP)
        val keySource = when (mode) {
            "SSH_ONLY" -> sshPassword ?: ""
            "PAYLOAD_ONLY" -> userKey ?: ""
            "SSH_PAYLOAD" -> ((sshPassword ?: "") + (userKey ?: ""))
            else -> ""
        }
        val key = sha256(keySource.toByteArray(Charsets.UTF_8) + salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun sha256(b: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(b)
}
