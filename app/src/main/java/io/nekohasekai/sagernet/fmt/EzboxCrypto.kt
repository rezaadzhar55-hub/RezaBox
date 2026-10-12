package io.nekohasekai.sagernet.fmt

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ezbox Tunnel profil terenkripsi (.rz).
 *
 * Semua file export TANPA PIN. File tetap terenkripsi AES-256-GCM dengan kunci acak
 * yang ikut disimpan di file — tujuannya agar isi tidak terbaca di text editor,
 * bukan sekuransi terhadap pemilik file.
 *
 * Format file: "ezboxtunnel://" + b64url(JSON{salt,iv,ct})
 * Isi (plaintext) = JSON profil:
 *   {"name":..,"server":..,"port":..,"user":..,"password":..,
 *    "bughost":..,"payload":..,"proxy":..,"method":..,
 *    "lockPayload":bool,"lockProfile":bool}
 *
 * lockPayload=true  -> payload disembunyikan di pengaturan SSH (SSH bisa diedit)
 * lockProfile=true  -> semua field disembunyikan di pengaturan
 */
object EzboxCrypto {

    const val PREFIX = "ezboxtunnel://"

    fun export(json: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val meta = org.json.JSONObject()
            .put("s", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("i", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("k", Base64.encodeToString(key, Base64.NO_WRAP))
            .put("d", Base64.encodeToString(ct, Base64.NO_WRAP))
        return PREFIX + Base64.encodeToString(
            meta.toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
    }

    /** return JSON profil; throw jika file rusak */
    fun decrypt(content: String): String {
        val raw = content.trim().removePrefix(PREFIX)
        val meta = org.json.JSONObject(
            String(Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
        )
        val salt = Base64.decode(meta.getString("s"), Base64.NO_WRAP)
        val iv = Base64.decode(meta.getString("i"), Base64.NO_WRAP)
        val key = Base64.decode(meta.getString("k"), Base64.NO_WRAP)
        val ct = Base64.decode(meta.getString("d"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    fun isEzboxFile(content: String): Boolean = content.trim().startsWith(PREFIX)
}
