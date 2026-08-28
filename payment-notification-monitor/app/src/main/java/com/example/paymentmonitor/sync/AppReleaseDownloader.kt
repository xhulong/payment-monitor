package com.example.paymentmonitor.sync

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.paymentmonitor.BuildConfig
import java.io.File
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request

class AppReleaseDownloader(
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun download(context: Context, release: AppReleaseData): File {
        val url = requireNotNull(release.downloadUrl) { "暂无可用下载地址" }
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "APK 下载失败：HTTP ${response.code}" }
            val body = requireNotNull(response.body)
            val file = File(context.cacheDir, "payment-monitor-${release.versionName}.apk")
            body.byteStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            val actual = sha256(file)
            check(actual.equals(release.sha256, ignoreCase = true)) {
                "APK SHA-256 校验失败"
            }
            verifyIdentity(context, file, release)
            return file
        }
    }

    private fun verifyIdentity(context: Context, file: File, release: AppReleaseData) {
        val packageInfo = packageArchiveInfo(context.packageManager, file)
            ?: error("无法解析 APK 包信息")
        val expectedPackage = release.verifiedPackageName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: BuildConfig.APPLICATION_ID
        check(packageInfo.packageName == expectedPackage) {
            "APK 包名校验失败"
        }
        check(packageInfo.archiveVersionCode() == release.versionCode.toLong()) {
            "APK versionCode 与发布信息不一致"
        }
        check(packageInfo.versionName.orEmpty() == release.versionName) {
            "APK versionName 与发布信息不一致"
        }

        val expectedCertificate = normalizeSha256(release.signingCertificateSha256)
        check(expectedCertificate.length == 64) {
            "发布信息缺少有效的 APK 签名证书 SHA-256"
        }
        val certificates = packageInfo.archiveSignatures()
            .map { signature -> sha256(signature.toByteArray()) }
        check(certificates.any { it == expectedCertificate }) {
            "APK 签名证书校验失败"
        }
    }

    @Suppress("DEPRECATION")
    private fun packageArchiveInfo(packageManager: PackageManager, file: File): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_SIGNING_CERTIFICATES.toLong(),
                ),
            )
        } else {
            packageManager.getPackageArchiveInfo(
                file.absolutePath,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                },
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.archiveVersionCode(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun PackageInfo.archiveSignatures() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            signatures?.toList().orEmpty()
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } >= 0) {
                if (read == 0) continue
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value)
            .joinToString("") { "%02x".format(it) }
}

internal fun normalizeSha256(value: String): String =
    value.filterNot { it == ':' || it.isWhitespace() }.lowercase()
