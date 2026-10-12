package io.nekohasekai.sagernet.fmt

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * RezaBox profil terenkripsi (.rz).
 *
 * Kunci = PIN bebas yang dipilih user saat export (wajib).
 * Format file: "RZ1" + b64url(JSON{salt,iv,ct}) — metadata tidak mengandung data profil.
 * Enkripsi AES-256-GCM, key = SHA-256("rezabox|" + pin + "|" + salt).
 * Isi (plaintext) = JSON profil:
 *   {"name":..,"server":..,"port":..,"user":..,"password":..,
 *    "bughost":..,"payload":..,"proxy":..,"method":..,
 *    "lockPayload":bool,"lockProfile":bool}
 *
 * lockPayload=true  -> payload tidak bisa diedit saat profil dipakai (SSH bebas)
 * lockProfile=true  -> SSH (server/user/password) + payload tidak bisa diedit
 * dua-duanya false  -> profil bebas diedit; file tetap terenkripsi
 */
object RezaBoxCrypto {

    private const val MAGIC = "RZ1"

    fun export(json: String, pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = derive(pin, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val meta = org.json.JSONObject()
            .put("s", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("i", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("d", Base64.encodeToString(ct, Base64.NO_WRAP))
        return MAGIC + Base64.encodeToString(
            meta.toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
    }

    /** return JSON profil; throw jika PIN salah / file rusak */
    fun decrypt(content: String, pin: String): String {
        val raw = content.trim().removePrefix(MAGIC)
        val meta = org.json.JSONObject(
            String(Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        )
        val salt = Base64.decode(meta.getString("s"), Base64.NO_WRAP)
        val iv = Base64.decode(meta.getString("i"), Base64.NO_WRAP)
        val ct = Base64.decode(meta.getString("d"), Base64.NO_WRAP)
        val key = derive(pin, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    fun isRezaBoxFile(content: String): Boolean = content.trim().startsWith(MAGIC)

    private fun derive(pin: String, salt: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(("rezabox|" + pin + "|").toByteArray(Charsets.UTF_8) + salt)
}
