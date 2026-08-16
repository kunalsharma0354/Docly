package com.nexora.docly.secgen

import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuiltArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.Certificate
import java.util.Base64
import java.util.Properties
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SecGenPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val androidExt = target.extensions
            .findByType(ApplicationAndroidComponentsExtension::class.java)
            ?: return
        val sdkRoot = resolveSdkRoot(target)
        val apiKey = resolveApiKey(target.rootProject)
        androidExt.onVariants(androidExt.selector().all()) { variant ->
            val cap = variant.name.replaceFirstChar { it.uppercase() }
            val genName = "generateSecurityBlob$cap"

            val genProvider = target.tasks.register(genName, SecurityBlobTask::class.java) {
                group = "docly-security"
                description = "Embedds encrypted integrity blob (API key + fingerprint of every APK entry) into the APK"
                keystoreFile.set(File(System.getProperty("user.home"), ".android/debug.keystore"))
                storePassword.set("android")
                keyAlias.set("androiddebugkey")
                keyPassword.set("android")
                this.sdkRoot.set(sdkRoot)
                this.apiKey.set(apiKey)
            }

            val request = variant.artifacts
                .use(genProvider)
                .wiredWithDirectories(
                    SecurityBlobTask::inputAPK,
                    SecurityBlobTask::outputAPK
                )
                .toTransformMany(SingleArtifact.APK)

            genProvider.configure {
                this.transformationRequest.set(request)
            }
        }
    }

    private fun resolveSdkRoot(target: Project): String {
        System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }?.let { return it }
        System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }?.let { return it }
        val localProps = File(target.rootProject.projectDir, "local.properties")
        if (localProps.exists()) {
            val sdkDir = Properties().apply {
                localProps.inputStream().use { load(it) }
            }.getProperty("sdk.dir")
            if (!sdkDir.isNullOrBlank()) return sdkDir.replace("\\:", ":").replace("\\\\", "\\")
        }
        throw GradleException("Cannot locate Android SDK (ANDROID_HOME / local.properties)")
    }

    /**
     * Key is resolved at configuration time (config-cache safe) and passed as a
     * task input. Read from the gitignored local.properties (doclyApiKey=) first,
     * falling back to gradle/wrapper/gradle-wrapper.properties for legacy setups.
     */
    private fun resolveApiKey(root: Project): String {
        fun propsAt(file: File): Properties? = if (file.exists()) {
            Properties().apply { file.inputStream().use { load(it) } }
        } else null
        val fromLocal = propsAt(File(root.projectDir, "local.properties"))
            ?.getProperty("doclyApiKey")?.trim()
        if (!fromLocal.isNullOrBlank()) return fromLocal
        val fromWrapper = propsAt(File(root.projectDir, "gradle/wrapper/gradle-wrapper.properties"))
            ?.getProperty("doclyApiKey")?.trim()
        if (!fromWrapper.isNullOrBlank()) return fromWrapper
        throw GradleException(
            "doclyApiKey missing. Add 'doclyApiKey=<your key>' to local.properties " +
                "(or gradle/wrapper/gradle-wrapper.properties) and rebuild."
        )
    }
}

abstract class SecurityBlobTask : DefaultTask() {

    @get:InputFile
    abstract val keystoreFile: RegularFileProperty

    @get:Input
    abstract val storePassword: Property<String>

    @get:Input
    abstract val keyAlias: Property<String>

    @get:Input
    abstract val keyPassword: Property<String>

    @get:Input
    abstract val sdkRoot: Property<String>

    @get:Input
    abstract val apiKey: Property<String>

    @get:InputFiles
    abstract val inputAPK: DirectoryProperty

    @get:OutputDirectory
    abstract val outputAPK: DirectoryProperty

    @get:Internal
    abstract val transformationRequest: Property<ArtifactTransformationRequest<SecurityBlobTask>>

    @TaskAction
    fun transform() {
        transformationRequest.get().submit(this) { input: BuiltArtifact ->
            val inputFile = File(input.outputFile)
            val outputFile = File(outputAPK.get().asFile, inputFile.name)
            injectBlobInto(inputFile, outputFile)
            outputFile
        }
    }

    private fun injectBlobInto(inputApk: File, outputApk: File) {
        val fingerprint = computeFingerprint(inputApk)
        val apiKey = loadApiKey()
        val payload = buildString {
            appendLine("ver=1")
            appendLine("apikey=${Base64.getEncoder().encodeToString(apiKey.toByteArray())}")
            append("entries=").appendLine(Base64.getEncoder().encodeToString(fingerprint.toByteArray()))
        }
        val blob = encryptPayload(payload)
        val blobBytes = blob.toByteArray(Charsets.UTF_8)

        outputApk.parentFile.mkdirs()
        val unsigned = File(outputApk.parentFile, outputApk.name + ".unsigned")
        ZipOutputStream(unsigned.outputStream()).use { out ->
            ZipInputStream(inputApk.inputStream()).use { input ->
                var entry = input.nextEntry
                while (entry != null) {
                    val bytes = input.readBytes()
                    if (entry.name.isEmpty()) {
                        entry = input.nextEntry
                        continue
                    }
                    val copy = ZipEntry(entry.name)
                    if (entry.method == ZipEntry.STORED) {
                        copy.method = ZipEntry.STORED
                        copy.size = bytes.size.toLong()
                        copy.compressedSize = bytes.size.toLong()
                        copy.crc = CRC32().apply { update(bytes) }.value
                    } else {
                        copy.method = ZipEntry.DEFLATED
                    }
                    out.putNextEntry(copy)
                    out.write(bytes)
                    out.closeEntry()
                    entry = input.nextEntry
                }
            }
            out.putNextEntry(ZipEntry("assets/security.bin").apply {
                method = ZipEntry.DEFLATED
            })
            out.write(blobBytes)
            out.closeEntry()
        }
        val aligned = File(outputApk.parentFile, outputApk.name + ".aligned")
        align(unsigned, aligned)
        resign(aligned, outputApk)
        unsigned.delete()
        aligned.delete()
        logger.lifecycle("SECURITY BLOB embedded into ${outputApk.name} (${blobBytes.size} bytes)")
    }

    /**
     * Stored (uncompressed) entries like .so files under lib/ must stay
     * page-aligned so Android can mmap them directly (16 KB pages on Android 15+).
     */
    private fun align(unsigned: File, aligned: File) {
        val buildToolsDir = locateBuildTools()
        val zipalign = File(buildToolsDir, if (System.getProperty("os.name").startsWith("Windows")) "zipalign.exe" else "zipalign")
        if (!zipalign.exists()) {
            throw GradleException("zipalign not found in $buildToolsDir")
        }
        val cmd = listOf(zipalign.absolutePath, "-f", "-P", "16", "4", unsigned.absolutePath, aligned.absolutePath)
        val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        if (exit != 0) {
            throw GradleException("zipalign failed (exit $exit): $output")
        }
    }

    /**
     * The APK transform runs after AGP signed the APK, and AGP does not re-sign
     * transformed APKs. Re-sign with the same debug keystore via apksigner.
     */
    private fun resign(unsigned: File, signed: File) {
        val buildToolsDir = locateBuildTools()
        val apksigner = if (System.getProperty("os.name").startsWith("Windows")) {
            File(buildToolsDir, "apksigner.bat")
        } else {
            File(buildToolsDir, "apksigner")
        }
        if (!apksigner.exists()) {
            throw GradleException("apksigner not found in $buildToolsDir")
        }
        val tmp = File(signed.parentFile, signed.name + ".tmpsign")
        val cmd = listOf(
            apksigner.absolutePath,
            "sign",
            "--ks", keystoreFile.get().asFile.absolutePath,
            "--ks-pass", "pass:${storePassword.get()}",
            "--ks-key-alias", keyAlias.get(),
            "--key-pass", "pass:${keyPassword.get()}",
            "--out", tmp.absolutePath,
            unsigned.absolutePath
        )
        val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        if (exit != 0) {
            throw GradleException("apksigner failed (exit $exit): $output")
        }
        tmp.copyTo(signed, overwrite = true)
        tmp.delete()
    }

    private fun locateBuildTools(): File {
        val sdkRoot = sdkRoot.get()
        val buildToolsDir = File(sdkRoot, "build-tools")
        return buildToolsDir.listFiles()
            ?.filter { it.isDirectory && it.name.matches(Regex("\\d+\\.\\d+\\.\\d+")) }
            ?.maxByOrNull { it.name }
            ?: throw GradleException("No build-tools found in $buildToolsDir")
    }

    private fun computeFingerprint(apk: File): String {
        val hashes = mutableListOf<String>()
        ZipInputStream(apk.inputStream()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name.isEmpty() || name == "assets/security.bin" || name.startsWith("META-INF/") || name.endsWith("/")) {
                    entry = input.nextEntry
                    continue
                }
                val bytes = input.readBytes()
                hashes += "${name}:${sha256Hex(bytes)}"
                entry = input.nextEntry
            }
        }
        return hashes.sorted().joinToString("\n")
    }

    private fun loadApiKey(): String =
        apiKey.get().trim().ifBlank {
            throw GradleException("doclyApiKey missing - add it to local.properties and rebuild.")
        }

    private fun encryptPayload(payload: String): String {
        val cert = loadSigningCert()
        val encryptionKey = sha256(cert.encoded)
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(encryptionKey, "AES"),
            GCMParameterSpec(128, nonce)
        )
        val blob = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        return buildString {
            appendLine("DOCLY_SEC_V1")
            appendLine("nonce=${b64.encodeToString(nonce)}")
            appendLine("key=${b64.encodeToString(blob)}")
        }
    }

    private fun loadSigningCert(): Certificate {
        val ks = KeyStore.getInstance("PKCS12").apply {
            keystoreFile.get().asFile.inputStream().use {
                load(it, storePassword.get().toCharArray())
            }
        }
        return ks.getCertificate(keyAlias.get()) ?: throw GradleException("Key alias ${keyAlias.get()} not found")
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun sha256Hex(bytes: ByteArray): String =
        sha256(bytes).joinToString("") { "%02x".format(it) }
}