package com.nexora.docly.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Tamper-proof gate. Runs on app open (before UI).
 * - Decrypts the real API key (never shipped in plaintext/BuildConfig).
 * - Verifies the APK signature AND the byte-exact fingerprint of every
 *   entry in the packaged APK (manifest, dex, resources.arsc, res images,
 *   assets) computed at build time by the secgen plugin.
 * - Any single changed byte / rename / image edit / re-sign that does not
 *   match the build-time snapshot throws SecurityException -> app crashes.
 * - Detects root / Frida / emulator tooling environments on real devices.
 */
object Protect {

    private const val MAGIC = "DOCLY_SEC_V1"
    private var ready = false

    @Volatile
    private var apiKey: String? = null

    private val lock = Any()

    fun init(context: Context) {
        if (ready) return
        synchronized(lock) {
            if (ready) return
            verify(context)
            ready = true
        }
    }

    fun apiKey(): String =
        apiKey ?: throw SecurityException("DoclyProtect: API key not available - app integrity failed")

    fun isReady(): Boolean = ready

    private fun crash(reason: String): Nothing {
        // Global handler never catches -> process dies immediately.
        throw SecurityException("DoclyProtect: $reason")
    }

    private fun verify(context: Context) {
        val app = context.applicationContext

        // 1) Decrypt blob (AES-GCM keyed with signing certificate bytes)
        val parsed = readSecurityBlob(app) ?: crash("security blob missing/corrupt")
        val payloadText = decrypt(parsed.aesKey, parsed.nonce, parsed.cipherText) ?: crash("decrypt failed (signature tampered)")

        val payload = parsePayload(payloadText) ?: crash("payload corrupt")
        if (payload.ver != "1") crash("payload version mismatch")

        apiKey = payload.apiKey
        if (apiKey.isNullOrBlank()) crash("key missing")

        // 2) Signer certificate must be the same keystore that encrypted the blob.
        //    The blob AES key is derived from the cert, so a re-sign makes
        //    step 1 fail already; this extra check keeps it explicit.
        val signer = currentSignerBytes(app) ?: crash("signature unreadable")
        if (!messageDigest(signer).contentEquals(parsed.aesKey)) crash("signature mismatch")

        // 3) Verify the installed APK byte-exact fingerprint
        val apk = File(app.applicationInfo.sourceDir)
        val actualFingerprint = computeFingerprint(apk)
        if (actualFingerprint != payload.entries) crash("APK content modified (any byte tampered)")

        // 4) Runtime environment checks (emulator-safe, root/Frida blocked on real devices)
        if (!isEmulator()) {
            if (isRooted()) crash("rooted device detected")
            if (isFridaAttached()) crash("debug agent detected")
        }
    }

    // same algorithm as secgen-plugin SecurityBlobTask.computeFingerprint
    private fun computeFingerprint(apk: File): String {
        val hashes = mutableListOf<String>()
        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (name.isEmpty() || name == "assets/security.bin" || name.startsWith("META-INF/")) continue
                if (entry.isDirectory) continue
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                hashes += "$name:${sha256Hex(bytes)}"
            }
        }
        return hashes.sorted().joinToString("\n")
    }

    private data class SecurityBlob(
        val nonce: ByteArray,
        val cipherText: ByteArray,
        val aesKey: ByteArray
    )

    private data class Payload(
        val ver: String,
        val apiKey: String,
        val entries: String
    )

    private fun readSecurityBlob(context: Context): SecurityBlob? {
        return try {
            val text = context.assets.open("security.bin").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val lines = text.split("\n")
            if (lines.getOrNull(0) != MAGIC) return null
            var nonce: ByteArray? = null
            var cipher: ByteArray? = null
            val signer = currentSignerBytes(context) ?: return null
            val keyBytes = sha256(signer)
            for (line in lines.drop(1)) {
                val idx = line.indexOf('=')
                if (idx <= 0) continue
                when (line.substring(0, idx)) {
                    "nonce" -> nonce = Base64.decode(line.substring(idx + 1), Base64.DEFAULT)
                    "key" -> cipher = Base64.decode(line.substring(idx + 1), Base64.DEFAULT)
                }
            }
            if (nonce == null || cipher == null) return null
            SecurityBlob(nonce!!, cipher!!, keyBytes)
        } catch (_: Exception) {
            null
        }
    }

    private fun decrypt(key: ByteArray, nonce: ByteArray, cipherText: ByteArray): String? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        String(cipher.doFinal(cipherText), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private fun parsePayload(text: String): Payload? = try {
        val map = text.split("\n").mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
        }.toMap()
        Payload(
            ver = map["ver"].orEmpty(),
            apiKey = map["apikey"]?.let { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }.orEmpty(),
            entries = map["entries"]?.let { String(Base64.decode(it.trim(), Base64.DEFAULT), Charsets.UTF_8) }.orEmpty()
        )
    } catch (_: Exception) {
        null
    }

    private fun currentSignerBytes(context: Context): ByteArray? = try {
        val pi = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
        pi.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
    } catch (_: Exception) {
        null
    }

    private fun messageDigest(bytes: ByteArray): ByteArray = sha256(bytes)

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun sha256Hex(bytes: ByteArray): String =
        sha256(bytes).joinToString("") { "%02x".format(it) }

    // ---- environment checks ----

    private fun isEmulator(): Boolean {
        val f = Build.FINGERPRINT
        val model = Build.MODEL
        val abi = Build.SUPPORTED_ABIS.joinToString(",")
        return f.startsWith("generic") || f.contains("emulator") ||
            model.contains("sdk_") || model.contains("Google APIs") ||
            model.contains("Emulator") || abi.contains("x86")
    }

    private fun isRooted(): Boolean {
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/system/app/Superuser.apk", "/system/etc/init.d/99SuperSUDaemon",
            "/system/bin/.ext/.su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/xbin/magisk", "/sbin/magisk", "/data/adb/magisk"
        )
        return paths.any { File(it).exists() }
    }

    private fun isFridaAttached(): Boolean {
        return try {
            val maps = File("/proc/self/maps")
            if (!maps.exists()) return false
            val text = maps.inputStream().bufferedReader(Charsets.UTF_8).use { r ->
                r.readText().take(512_000)
            }
            text.contains("frida") || text.contains("gum-js-loop") || text.contains("gmain")
        } catch (_: Exception) {
            false
        }
    }
}