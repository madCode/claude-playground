package com.app.carry

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

/**
 * What an APK says about itself, or the running app about itself: enough to tell whether one may
 * replace the other.
 *
 * @param signers SHA-256 of each certificate that signed the APK's contents.
 */
internal data class ApkFacts(val packageName: String, val versionCode: Long, val signers: Set<String>)

internal sealed interface Verdict {
    data object Good : Verdict

    /** Not this app, or not readable: never to be installed. */
    data class Bad(val why: String) : Verdict

    /** This app, but not newer than what's installed: CI uploads the fixed-name file a moment
     *  after the numbered one, so it can lag. */
    data class Stale(val why: String) : Verdict
}

/**
 * Whether [apk] may install over [installed]. Android refuses a different key itself, but only
 * once the owner has been asked; this keeps a tampered or mismatched file from getting that far.
 */
internal fun verdict(apk: ApkFacts?, installed: ApkFacts): Verdict = when {
    apk == null -> Verdict.Bad("it isn't an app this phone can read")
    apk.packageName != installed.packageName -> Verdict.Bad("it's ${apk.packageName}, not ${installed.packageName}")
    // Empty sets would compare equal: no certificate read is no proof.
    apk.signers.isEmpty() || installed.signers.isEmpty() -> Verdict.Bad("its signature couldn't be read")
    apk.signers != installed.signers -> Verdict.Bad("it's signed with a different key")
    apk.versionCode <= installed.versionCode -> Verdict.Stale("build ${apk.versionCode} isn't newer than ${installed.versionCode}")
    else -> Verdict.Good
}

/** The facts of the APK at [file], or null if Android can't parse it. */
internal fun PackageManager.apkFacts(file: File): ApkFacts? {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            // Parsed without its signing info, an archive is read the older way rather than
            // passed unchecked.
            ?.takeIf { it.signingInfo != null } ?: legacyArchiveInfo(file)
    } else {
        legacyArchiveInfo(file)
    }
    return info?.facts()
}

/** The running app's own facts, the yardstick for [apkFacts]. */
internal fun PackageManager.installedFacts(packageName: String): ApkFacts {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    } else {
        @Suppress("DEPRECATION") getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
    }
    return info.facts()
}

@Suppress("DEPRECATION") // GET_SIGNATURES is the only way below API 28, and a fallback above.
private fun PackageManager.legacyArchiveInfo(file: File): PackageInfo? =
    getPackageArchiveInfo(file.path, PackageManager.GET_SIGNATURES)

private fun PackageInfo.facts(): ApkFacts {
    // The certificates that signed this APK, not older ones a rotated key vouches for: the debug
    // key never rotates, so anything else is a different signer.
    val certs: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && signingInfo != null) {
        signingInfo?.apkContentsSigners
    } else {
        @Suppress("DEPRECATION") signatures
    }
    return ApkFacts(packageName, PackageInfoCompat.getLongVersionCode(this), certs.orEmpty().map { sha256(it.toByteArray()) }.toSet())
}

private fun sha256(bytes: ByteArray) =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
