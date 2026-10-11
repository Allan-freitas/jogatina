package br.com.jogatina.data.device

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.util.Locale
import java.util.TimeZone

/**
 * Ficha do aparelho enviada no login (tudo sem permissões especiais).
 * ANDROID_ID identifica a instalação (muda em factory reset).
 */
data class DeviceInfo(
    val platform: String = "android",
    val model: String?,
    val manufacturer: String?,
    val osVersion: String?,
    val sdkVersion: String?,
    val appVersion: String?,
    val locale: String?,
    val timezone: String?,
    val deviceId: String?
) {
    fun toJson(): JSONObject = JSONObject()
        .put("platform", platform)
        .putOpt("model", model)
        .putOpt("manufacturer", manufacturer)
        .putOpt("osVersion", osVersion)
        .putOpt("sdkVersion", sdkVersion)
        .putOpt("appVersion", appVersion)
        .putOpt("locale", locale)
        .putOpt("timezone", timezone)
        .putOpt("deviceId", deviceId)

    companion object {
        @SuppressLint("HardwareIds")
        fun collect(context: Context): DeviceInfo {
            val locale = Locale.getDefault()
            val appVersion = try {
                val pm = context.packageManager
                val p = pm.getPackageInfo(context.packageName, 0)
                val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    p.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    p.versionCode.toLong()
                }
                "${p.versionName} ($code)"
            } catch (_: Exception) {
                null
            }
            return DeviceInfo(
                model = Build.MODEL,
                manufacturer = Build.MANUFACTURER,
                osVersion = Build.VERSION.RELEASE,
                sdkVersion = Build.VERSION.SDK_INT.toString(),
                appVersion = appVersion,
                locale = locale.toLanguageTag(),
                timezone = TimeZone.getDefault().id,
                deviceId = try {
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                } catch (_: Exception) {
                    null
                }
            )
        }
    }
}
