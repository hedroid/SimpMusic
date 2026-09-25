package com.maxrave.simpmusic

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.security.MessageDigest

/** SHA-256 hex of each certificate this APK is signed with — the form F-Droid pins as AllowedAPKSigningKeys. */
fun Context.signingCertSha256(): List<String> {
    val signatures =
        packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
    return signatures.orEmpty().map { signature ->
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
