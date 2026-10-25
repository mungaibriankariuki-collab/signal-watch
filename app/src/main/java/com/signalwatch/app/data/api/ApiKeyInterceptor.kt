package com.signalwatch.app.data.api

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.signalwatch.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest

/**
 * Adds the API key, plus the X-Android-Package / X-Android-Cert headers that Google checks
 * when the key is restricted to "Android apps". Without them a restricted key is rejected,
 * because REST calls made with Retrofit don't send them on their own.
 */
class ApiKeyInterceptor(context: Context) : Interceptor {

    private val packageName = context.packageName
    private val certSha1 = signingCertSha1(context)

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url.newBuilder().addQueryParameter("key", BuildConfig.YOUTUBE_API_KEY).build()
        val request = original.newBuilder()
            .url(url)
            .header("X-Android-Package", packageName)
            .apply { certSha1?.let { header("X-Android-Cert", it) } }
            .build()
        return chain.proceed(request)
    }

    private fun signingCertSha1(context: Context): String? = runCatching {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val cert = signatures?.firstOrNull()?.toByteArray() ?: return null
        MessageDigest.getInstance("SHA-1").digest(cert).joinToString("") { "%02X".format(it) }
    }.getOrNull()
}
