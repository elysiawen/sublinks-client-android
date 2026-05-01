package com.github.kr328.clash

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import com.github.kr328.clash.design.R as DesignR

object SubLinksService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private const val PREFS_NAME = "sublinks_prefs"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_REFRESH_TOKEN = "jwt_refresh_token"
    private const val KEY_SERVER = "server_url"
    private const val KEY_USER = "user_info"
    private val USER_AGENT = "SubLinks Client Android/${BuildConfig.VERSION_NAME}"

    @androidx.annotation.Keep
    data class LoginRequest(val username: String, val password: String, val code: String? = null, val deviceInfo: String? = null)
    @androidx.annotation.Keep
    data class LoginResponse(val token: String?, val accessToken: String?, val access_token: String?, val refreshToken: String?, val user: Any?, val error: String?, val requires2FA: Boolean? = null, val message: String? = null)
    
    sealed class LoginResult {
        object Success : LoginResult()
        data class Requires2FA(val message: String) : LoginResult()
        data class Error(val message: String) : LoginResult()
    }
    @androidx.annotation.Keep
    data class RefreshResponse(val accessToken: String?, val access_token: String?, val error: String?)
    @androidx.annotation.Keep
    data class LogoutResponse(val success: Boolean, val message: String?)
    @androidx.annotation.Keep
    data class Subscription(val name: String, val url: String, val enabled: Boolean = true)
    @androidx.annotation.Keep
    data class SubscriptionsResponse(val subscriptions: List<Subscription>)

    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_TOKEN)
    }

    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, null)
    }

    fun getServerUrl(context: Context): String? {
        return BuildConfig.SUBLINKS_API_URL
    }

    suspend fun login(context: Context, serverUrl: String, username: String, password: String, code: String? = null): LoginResult {
        return withContext(Dispatchers.IO) {
            try {
                // Ensure server url doesn't end with slash
                val cleanUrl = serverUrl.trim().removeSuffix("/")
                val url = "$cleanUrl/api/client/auth/login"
                
                val deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})"
                val json = gson.toJson(LoginRequest(username, password, code, deviceInfo))
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw IOException("Empty response")
                
                // Try to parse JSON regardless of status code to find error message
                val loginResponse = try {
                    gson.fromJson(responseBody, LoginResponse::class.java)
                } catch (e: Exception) {
                    null
                }

                if (response.isSuccessful && loginResponse?.requires2FA == true) {
                    return@withContext LoginResult.Requires2FA(loginResponse?.message ?: "该账户已启用两步验证，请提供 TOTP 验证码")
                }

                if (!response.isSuccessful) {
                    val errorMsg = loginResponse?.error ?: "HTTP ${response.code}: ${response.message}"
                    return@withContext LoginResult.Error(errorMsg)
                }

                loginResponse ?: return@withContext LoginResult.Error("Invalid JSON response")
                
                val token = loginResponse.token ?: loginResponse.accessToken ?: loginResponse.access_token
                val refreshToken = loginResponse.refreshToken
                
                if (token != null) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString(KEY_TOKEN, token)
                        .putString(KEY_REFRESH_TOKEN, refreshToken)
                        .putString(KEY_SERVER, cleanUrl)
                        .putString(KEY_USER, gson.toJson(loginResponse.user))
                        .apply()
                    LoginResult.Success // Success
                } else {
                    LoginResult.Error(loginResponse.error ?: "Login failed: No token received")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                LoginResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun refreshAccessToken(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) 
                    ?: return@withContext false
                val serverUrl = getServerUrl(context) ?: return@withContext false
                
                val url = "$serverUrl/api/client/auth/refresh"
                val json = gson.toJson(mapOf("refreshToken" to refreshToken))
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext false
                
                if (!response.isSuccessful) {
                    // Refresh token also expired, need to re-login
                    return@withContext false
                }
                
                val refreshResponse = gson.fromJson(responseBody, RefreshResponse::class.java)
                val newToken = refreshResponse.accessToken ?: refreshResponse.access_token
                
                if (newToken != null) {
                    prefs.edit()
                        .putString(KEY_TOKEN, newToken)
                        .apply()
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    class AuthenticationException(message: String) : IOException(message)

    private suspend fun executeWithAuthRetry(context: Context, request: Request): okhttp3.Response {
        var response = client.newCall(request).execute()
        if (response.code == 401 || response.code == 403) {
            if (refreshAccessToken(context)) {
                val newToken = getToken(context) ?: throw AuthenticationException("Token refresh failed")
                val retryRequest = request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                response = client.newCall(retryRequest).execute()
            } else {
                throw AuthenticationException("Authentication failed: Token expired")
            }
        }
        return response
    }

    suspend fun fetchSubscriptions(context: Context): List<Subscription> {
        return withContext(Dispatchers.IO) {
            val token = getToken(context) ?: throw AuthenticationException("No token found")
            val serverUrl = getServerUrl(context) ?: throw IOException("No server URL found")

            val url = "$serverUrl/api/client/subscriptions"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val response = executeWithAuthRetry(context, request)

            if (!response.isSuccessful) {
                throw IOException("Fetch failed: HTTP ${response.code}")
            }

            val responseBody = response.body?.string() ?: throw IOException("Empty response")
            val subResponse = gson.fromJson(responseBody, SubscriptionsResponse::class.java)

            subResponse.subscriptions
        }
    }

    @androidx.annotation.Keep
    data class UserInfo(val username: String, val level: Int? = 0, val traffic: Long? = 0)

    fun getUsername(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userJson = prefs.getString(KEY_USER, null) ?: return null
        return try {
            val element = com.google.gson.JsonParser.parseString(userJson)
            if (element.isJsonObject) {
                // Try fetching "username" first, fallback to "sub_links_user" etc if structure varies
                val obj = element.asJsonObject
                if (obj.has("nickname") && !obj.get("nickname").isJsonNull && obj.get("nickname").asString.isNotEmpty()) {
                    obj.get("nickname").asString
                } else if (obj.has("username")) {
                    obj.get("username").asString
                } else {
                    "User"
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getAvatar(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userJson = prefs.getString(KEY_USER, null) ?: return null
        return try {
            val element = com.google.gson.JsonParser.parseString(userJson)
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                if (obj.has("avatar") && !obj.get("avatar").isJsonNull) {
                    obj.get("avatar").asString
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @androidx.annotation.Keep
    data class HitokotoResponse(val hitokoto: String, val from: String)

    suspend fun fetchUserInfo(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            val token = getToken(context) ?: return@withContext false
            val serverUrl = getServerUrl(context) ?: return@withContext false

            val url = "$serverUrl/api/client/auth/user"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val response = executeWithAuthRetry(context, request)

            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrEmpty()) {
                    // Validate JSON
                    try {
                        val element = com.google.gson.JsonParser.parseString(body)
                        if (element.isJsonObject) {
                             val obj = element.asJsonObject
                             val userToSave = if (obj.has("user") && obj.get("user").isJsonObject) {
                                 obj.get("user").toString()
                             } else {
                                 body
                             }
                             
                             val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                             prefs.edit().putString(KEY_USER, userToSave).apply()
                             return@withContext true
                        }
                    } catch (e: Exception) {
                        // Ignore invalid JSON
                    }
                }
            }
            false
        }
    }

    suspend fun fetchHitokoto(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://v1.hitokoto.cn/?c=a")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                val hitokoto = gson.fromJson(body, HitokotoResponse::class.java)
                hitokoto.hitokoto
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun fetchRandomImage(context: Context): android.graphics.Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val store = com.github.kr328.clash.service.store.SubLinksStore(context)
                val type = store.heroBackgroundType

                when (type) {
                    "network", "url", "api" -> {
                        val value = store.heroNetworkUrl
                        val url = if (value.isEmpty()) com.github.kr328.clash.service.store.SubLinksStore.DEFAULT_BACKGROUND_URL else value
                        if (url.isEmpty()) return@withContext null
                        
                        val request = Request.Builder()
                            .url(url)
                            .get()
                            .build()
                        val response = client.newCall(request).execute()
                        val stream = response.body?.byteStream()
                        if (stream != null) {
                           android.graphics.BitmapFactory.decodeStream(stream)
                        } else {
                            null
                        }
                    }
                    "local" -> {
                        val value = store.heroLocalUri
                        if (value.isEmpty()) return@withContext null
                        try {
                            val uri = android.net.Uri.parse(value)
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                android.graphics.BitmapFactory.decodeStream(stream)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                    "color" -> {
                        val value = store.heroColorCode
                        if (value.isEmpty()) return@withContext null
                        try {
                            if (value.startsWith("gradient:")) {
                                val colors = value.removePrefix("gradient:").split(",").map { android.graphics.Color.parseColor(it) }
                                if (colors.size >= 2) {
                                    val width = 100
                                    val height = 100
                                    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(bitmap)
                                    val shader = android.graphics.LinearGradient(
                                        0f, 0f, 0f, height.toFloat(),
                                        colors.toIntArray(),
                                        null,
                                        android.graphics.Shader.TileMode.CLAMP
                                    )
                                    val paint = android.graphics.Paint().apply { this.shader = shader }
                                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                                    bitmap
                                } else null
                            } else {
                                val color = android.graphics.Color.parseColor(value)
                                val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(color)
                                bitmap
                            }
                        } catch (e: Exception) {
                             null
                        }
                    }
                    else -> null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun logout(context: Context): Pair<Boolean, String?> {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
            val serverUrl = getServerUrl(context)
            
            var result: Pair<Boolean, String?> = false to null

            // Attempt to notify server about logout
            if (refreshToken != null && serverUrl != null) {
                try {
                    // Ensure server url doesn't end with slash (redundant check but safe)
                    val cleanUrl = serverUrl.trim().removeSuffix("/")
                    val url = "$cleanUrl/api/client/auth/logout"
                    val json = gson.toJson(mapOf("refreshToken" to refreshToken))
                    val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                    
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .post(body)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string()
                    
                    if (responseBody != null) {
                         try {
                              val apiResponse = gson.fromJson(responseBody, LogoutResponse::class.java)
                              if (apiResponse != null) {
                                  result = apiResponse.success to apiResponse.message
                              }
                         } catch (e: Exception) {
                              result = false to "Invalid response"
                         }
                    } else {
                         result = false to "Empty response"
                    }
                    
                    if (!response.isSuccessful && result.second == null) {
                         result = false to "HTTP ${response.code}"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    result = false to e.message
                }
            } else {
                result = true to "Local logout only"
            }

            // Always clear auth data
            prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_SERVER)
                .remove(KEY_USER)
                .apply()

            try {
                com.github.kr328.clash.util.withProfile {
                    queryAll().forEach {
                        if (it.type == com.github.kr328.clash.service.model.Profile.Type.Url) {
                            delete(it.uuid)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            result
        }
    }

    fun getGreeting(context: Context): String {
        val username = getUsername(context) ?: "User"
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val resId = when (hour) {
            in 0..4 -> DesignR.string.greeting_early_morning
            in 5..10 -> DesignR.string.greeting_morning
            in 11..13 -> DesignR.string.greeting_noon
            in 14..18 -> DesignR.string.greeting_afternoon
            in 19..22 -> DesignR.string.greeting_evening
            else -> DesignR.string.greeting_night
        }
        return context.getString(resId, username)
    }

    @androidx.annotation.Keep
    data class QrScanRequest(val token: String)
    @androidx.annotation.Keep
    data class QrScanResult(val ip: String, val ua: String)
    @androidx.annotation.Keep
    data class QrScanResponse(val success: Boolean, val data: QrScanResult?, val error: String?)
    @androidx.annotation.Keep
    data class QrConfirmRequest(val token: String)
    @androidx.annotation.Keep
    data class QrConfirmResponse(val success: Boolean, val message: String?)

    @androidx.annotation.Keep
    data class QrRejectRequest(val token: String)

    suspend fun qrReject(context: Context, token: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val authToken = getToken(context) ?: return@withContext false
                val serverUrl = getServerUrl(context) ?: return@withContext false
                val cleanUrl = serverUrl.trim().removeSuffix("/")
                val url = "$cleanUrl/api/client/auth/qr/reject"

                val json = gson.toJson(QrRejectRequest(token))
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $authToken")
                    .header("User-Agent", USER_AGENT)
                    .post(body)
                    .build()

                val response = executeWithAuthRetry(context, request)
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }

    @androidx.annotation.Keep
    data class IpInfoResponse(
        val status: String,
        val country: String,
        val regionName: String,
        val city: String,
        val isp: String,
        val query: String
    )

    suspend fun fetchIpInfo(ip: String): IpInfoResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://ip-api.com/json/$ip?lang=zh-CN")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                gson.fromJson(body, IpInfoResponse::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun parseUserAgent(ua: String): String {
        return when {
            ua.contains("Windows", ignoreCase = true) -> "Windows"
            ua.contains("Macintosh", ignoreCase = true) || ua.contains("Mac OS", ignoreCase = true) -> "macOS"
            ua.contains("Android", ignoreCase = true) -> "Android"
            ua.contains("iPhone", ignoreCase = true) || ua.contains("iPad", ignoreCase = true) || ua.contains("iOS", ignoreCase = true) -> "iOS"
            ua.contains("Linux", ignoreCase = true) -> "Linux"
            else -> "Unknown Device"
        }
    }

    suspend fun qrScan(context: Context, token: String): QrScanResult? {
        return withContext(Dispatchers.IO) {
            try {
                // No auth header needed for scan, just token in body? 
                // Doc says: POST /api/client/auth/qr/scan
                // Body: { "token": "..." }
                // Response: { "success": true, "data": { "ip": "...", "ua": "..." } }

                val serverUrl = getServerUrl(context) ?: throw IOException("No server URL")
                val cleanUrl = serverUrl.trim().removeSuffix("/")
                val url = "$cleanUrl/api/client/auth/qr/scan"
                
                val json = gson.toJson(QrScanRequest(token))
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: throw IOException("Empty response")
                
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: $responseBody")
                }

                val apiResponse = gson.fromJson(responseBody, QrScanResponse::class.java)
                if (apiResponse.success && apiResponse.data != null) {
                    apiResponse.data
                } else {
                    throw IOException(apiResponse.error ?: "Unknown error")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    suspend fun qrConfirm(context: Context, token: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val authToken = getToken(context) ?: throw AuthenticationException("Not logged in")
                val serverUrl = getServerUrl(context) ?: throw IOException("No server URL")
                val cleanUrl = serverUrl.trim().removeSuffix("/")

                val url = "$cleanUrl/api/client/auth/qr/confirm"
                val json = gson.toJson(QrConfirmRequest(token))
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $authToken")
                    .header("User-Agent", USER_AGENT)
                    .post(body)
                    .build()

                val response = executeWithAuthRetry(context, request)

                if (!response.isSuccessful) {
                    val errBody = response.body?.string()
                    throw IOException("HTTP ${response.code}: $errBody")
                }

                true
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    private fun generateUrlSuffix(url: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            digest.update(url.toByteArray())
            val messageDigest = digest.digest()
            val hexString = StringBuilder()
            for (activity in messageDigest) {
                var h = Integer.toHexString(0xFF and activity.toInt())
                while (h.length < 2) h = "0$h"
                hexString.append(h)
            }
            hexString.toString().substring(0, 5)
        } catch (e: Exception) {
            url.takeLast(5).filter { it.isLetterOrDigit() }
        }
    }

    suspend fun sync(context: Context, onProgress: suspend (String) -> Unit) {
        val subscriptions = fetchSubscriptions(context).filter { it.enabled }

        com.github.kr328.clash.util.withProfile {
            val currentProfiles = queryAll()
            
            val usedNames = java.util.HashSet<String>()

            subscriptions.forEach { sub ->
                var targetName = sub.name
                
                // If name collision in this batch, append URL-based suffix
                while (usedNames.contains(targetName)) {
                     // Check if it's already suffixed correctly? 
                     // No, "while" implies we keep trying? 
                     // But URL suffix is deterministic. If multiple have the same name and same URL hash? (Collision)
                     // Unlikely for 5 chars but possible. BUT user asked specifically for strictly "URL generated 5 chars".
                     // So we just add it once.
                     // Wait, if 3 subs have same name and same URL, they are duplicates anyway? No user said "duplicate name".
                     // If 3 subs have same name but diff URLs.
                     // Sub A (Name X, URL A) -> targetName = X
                     // Sub B (Name X, URL B) -> targetName = X -> match -> X_suffixB
                     // Sub C (Name X, URL C) -> targetName = X -> match -> X_suffixC
                     
                     // Issue: What if Sub B and Sub C has collision in suffix? (Very rare).
                     // But simpler logic:
                     targetName = sub.name + "_" + generateUrlSuffix(sub.url)
                     
                     // If still collision (e.g. identical URL? or hash collision?), break loop to avoid infinite?
                     // If key exists?
                     if (usedNames.contains(targetName)) {
                         // Still collision. Fallback to random or just leave it?
                         // User said "use subscription link generate 5 chars". 
                         // Assuming distinct URLs mean distinct suffixes usually. 
                         // Let's assume distinct. If match, break?
                         break
                     }
                }
                usedNames.add(targetName)
                
                onProgress(context.getString(DesignR.string.format_fetching_configuration, targetName))

                // Find existing by Source (perfect match) or Name (fallback)
                val existing = currentProfiles.find { it.source == sub.url || it.name == targetName }

                if (existing != null) {
                    if (existing.source != sub.url || existing.name != targetName) {
                        patch(existing.uuid, targetName, sub.url, 0)
                        commit(existing.uuid)
                        update(existing.uuid)
                    }
                } else {
                    val uuid = create(com.github.kr328.clash.service.model.Profile.Type.Url, targetName, sub.url)
                    commit(uuid)
                    update(uuid)

                    if (queryActive() == null) {
                        val newProfile = queryByUUID(uuid)
                        if (newProfile != null) {
                            setActive(newProfile)
                        }
                    }
                }
            }
            
            // Delete stale profiles
            // A profile is active if its source matches any sub OR its name matches any of our usedNames
            currentProfiles.forEach { profile ->
                 if (profile.imported && profile.type == com.github.kr328.clash.service.model.Profile.Type.Url) {
                     // 1. Matched by Source? (Most reliable)
                     val sourceMatch = subscriptions.any { it.url == profile.source }
                     
                     // 2. Matched by Name? (If source changed but name persisted)
                     val nameMatch = usedNames.contains(profile.name)
                     
                     if (!sourceMatch && !nameMatch) {
                         delete(profile.uuid)
                     }
                 }
            }
        }
    }
}
