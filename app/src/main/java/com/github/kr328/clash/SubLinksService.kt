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
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.design.R as DesignR

object SubLinksService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept-Language", java.util.Locale.getDefault().language)
                .build()
            chain.proceed(request)
        }
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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER, null) ?: BuildConfig.SUBLINKS_API_URL
    }

    private fun cleanUrl(url: String): String = url.trim().removeSuffix("/")

    suspend fun login(context: Context, serverUrl: String, username: String, password: String, code: String? = null): LoginResult {
        return withContext(Dispatchers.IO) {
            try {
                // Ensure server url doesn't end with slash
                val cleanUrl = cleanUrl(serverUrl)
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
                val responseBody = response.use { it.body?.string() } ?: throw IOException("Empty response")

                val loginResponse = try {
                    gson.fromJson(responseBody, LoginResponse::class.java)
                } catch (e: Exception) {
                    null
                }

                if (response.isSuccessful && loginResponse?.requires2FA == true) {
                    return@withContext LoginResult.Requires2FA(loginResponse?.message ?: context.getString(DesignR.string.login_2fa_required))
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
                    val editor = prefs.edit()
                        .putString(KEY_TOKEN, token)
                        .putString(KEY_SERVER, cleanUrl)
                        .putString(KEY_USER, gson.toJson(loginResponse.user))
                    if (refreshToken != null) {
                        editor.putString(KEY_REFRESH_TOKEN, refreshToken)
                    } else {
                        editor.remove(KEY_REFRESH_TOKEN)
                    }
                    editor.apply()
                    LoginResult.Success
                } else {
                    LoginResult.Error(loginResponse.error ?: "Login failed: No token received")
                }
            } catch (e: Exception) {
                Log.e("Login failed", e)
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
                val responseBody = response.use { it.body?.string() } ?: return@withContext false
                
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
                Log.e("Token refresh failed", e)
                false
            }
        }
    }

    class AuthenticationException(message: String) : IOException(message)

    private suspend fun executeWithAuthRetry(context: Context, request: Request): okhttp3.Response {
        val response = client.newCall(request).execute()
        if (response.code == 401 || response.code == 403) {
            response.close()
            if (refreshAccessToken(context)) {
                val newToken = getToken(context) ?: throw AuthenticationException("Token refresh failed")
                val retryRequest = request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return client.newCall(retryRequest).execute()
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
                val errBody = response.use { it.body?.string() }
                val serverMsg = try {
                    gson.fromJson(errBody, com.google.gson.JsonObject::class.java)?.get("error")?.asString
                } catch (_: Exception) { null }
                throw IOException(serverMsg ?: errBody ?: "Fetch failed: HTTP ${response.code}")
            }

            val responseBody = response.use { it.body?.string() } ?: throw IOException("Empty response")
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
            Log.w("Failed to parse username", e)
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
            Log.w("Failed to parse avatar", e)
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
                val body = response.use { it.body?.string() }
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
                        Log.w("Failed to parse user info JSON", e)
                    }
                }
            } else {
                response.close()
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
                val body = response.use { it.body?.string() } ?: return@withContext null
                val hitokoto = gson.fromJson(body, HitokotoResponse::class.java)
                hitokoto.hitokoto
            } catch (e: Exception) {
                Log.w("Failed to fetch hitokoto", e)
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
                        response.use { resp ->
                            resp.body?.byteStream()?.use { stream ->
                                android.graphics.BitmapFactory.decodeStream(stream)
                            }
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
                            Log.w("Failed to load local image", e)
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
                            Log.w("Failed to generate color bitmap", e)
                             null
                        }
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Log.w("Failed to fetch random image", e)
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
                    val cleanUrl = cleanUrl(serverUrl)
                    val url = "$cleanUrl/api/client/auth/logout"
                    val json = gson.toJson(mapOf("refreshToken" to refreshToken))
                    val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                    
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .post(body)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.use { it.body?.string() }
                    
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
                    Log.e("Logout request failed", e)
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
                Log.w("Failed to delete profiles on logout", e)
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
    data class QrConfirmResponse(val success: Boolean, val message: String?, val error: String?)

    @androidx.annotation.Keep
    data class QrRejectRequest(val token: String)

    suspend fun qrReject(context: Context, token: String): Pair<Boolean, String?> {
        return withContext(Dispatchers.IO) {
            try {
                val authToken = getToken(context) ?: return@withContext false to null
                val serverUrl = getServerUrl(context) ?: return@withContext false to null
                val cleanUrl = cleanUrl(serverUrl)
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
                val responseBody = response.use { it.body?.string() }

                if (!response.isSuccessful) {
                    val apiError = try {
                        gson.fromJson(responseBody, QrConfirmResponse::class.java)
                    } catch (_: Exception) { null }
                    return@withContext false to (apiError?.error ?: responseBody)
                }

                val apiResponse = try {
                    gson.fromJson(responseBody, QrConfirmResponse::class.java)
                } catch (_: Exception) { null }

                (apiResponse?.success ?: true) to (apiResponse?.message)
            } catch (e: Exception) {
                false to e.message
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

    private val IP_PATTERN = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$|^[0-9a-fA-F:]+$""")
    private val PRIVATE_IP_PATTERNS = listOf(
        Regex("""^10\."""),                      // 10.0.0.0/8
        Regex("""^172\.(1[6-9]|2\d|3[01])\."""), // 172.16.0.0/12
        Regex("""^192\.168\."""),                 // 192.168.0.0/16
        Regex("""^127\."""),                      // 127.0.0.0/8 (loopback)
        Regex("""^0\."""),                        // 0.0.0.0/8
        Regex("""^169\.254\."""),                // 169.254.0.0/16 (link-local)
        Regex("""^100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\."""), // 100.64.0.0/10 (CGNAT)
        Regex("""^::1$"""),                       // IPv6 loopback
        Regex("""^[fF][cCdD]"""),                 // IPv6 unique local (fc00::/7)
        Regex("""^[fF][eE][89aAbB]""")           // IPv6 link-local (fe80::/10)
    )

    fun isPrivateIp(ip: String): Boolean {
        return PRIVATE_IP_PATTERNS.any { it.containsMatchIn(ip) }
    }

    suspend fun fetchIpInfo(ip: String): IpInfoResponse? {
        Log.d("fetchIpInfo called with ip: $ip")
        if (!IP_PATTERN.matches(ip)) {
            Log.w("Invalid IP address: $ip")
            return null
        }
        if (isPrivateIp(ip)) {
            Log.d("Skipping ip-api.com for private IP: $ip")
            return null
        }
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("http://ip-api.com/json/$ip?lang=zh-CN")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                val body = response.use { it.body?.string() } ?: return@withContext null
                Log.d("ip-api.com response for $ip: $body")
                gson.fromJson(body, IpInfoResponse::class.java)
            } catch (e: Exception) {
                Log.w("Failed to fetch IP info for $ip", e)
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
            val serverUrl = getServerUrl(context) ?: throw IOException("No server URL")
            val cleanUrl = cleanUrl(serverUrl)
            val url = "$cleanUrl/api/client/auth/qr/scan"

            val json = gson.toJson(QrScanRequest(token))
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.use { it.body?.string() } ?: throw IOException("Empty response")

            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $responseBody")
            }

            val apiResponse = gson.fromJson(responseBody, QrScanResponse::class.java)
            if (apiResponse.success && apiResponse.data != null) {
                apiResponse.data
            } else {
                throw IOException(apiResponse.error ?: "Unknown error")
            }
        }
    }

    suspend fun qrConfirm(context: Context, token: String): Pair<Boolean, String?> {
        return withContext(Dispatchers.IO) {
            val authToken = getToken(context) ?: throw AuthenticationException("Not logged in")
            val serverUrl = getServerUrl(context) ?: throw IOException("No server URL")
            val cleanUrl = cleanUrl(serverUrl)

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
            val responseBody = response.use { it.body?.string() }

            if (!response.isSuccessful) {
                val apiError = try {
                    gson.fromJson(responseBody, QrConfirmResponse::class.java)
                } catch (_: Exception) { null }
                throw IOException(apiError?.error ?: responseBody ?: "HTTP ${response.code}")
            }

            val apiResponse = try {
                gson.fromJson(responseBody, QrConfirmResponse::class.java)
            } catch (_: Exception) { null }

            (apiResponse?.success ?: true) to (apiResponse?.message)
        }
    }

    // ── Device Code Flow (RFC 8628) ──────────────────────────────

    @androidx.annotation.Keep
    data class DeviceAuthorizeRequest(val deviceInfo: String)
    @androidx.annotation.Keep
    data class DeviceAuthorizeResponse(val deviceCode: String, val verificationUri: String, val expiresIn: Int, val interval: Int)
    @androidx.annotation.Keep
    data class DeviceTokenRequest(val deviceCode: String)
    @androidx.annotation.Keep
    data class DeviceTokenResponse(val success: Boolean?, val accessToken: String?, val refreshToken: String?, val user: Any?, val error: String?)

    @androidx.annotation.Keep
    data class DeviceErrorResponse(val error: String?)

    sealed class DeviceAuthorizeResult {
        data class Success(val response: DeviceAuthorizeResponse) : DeviceAuthorizeResult()
        data class Error(val message: String) : DeviceAuthorizeResult()
    }

    suspend fun deviceAuthorize(context: Context): DeviceAuthorizeResult {
        return withContext(Dispatchers.IO) {
            try {
                val serverUrl = getServerUrl(context) ?: return@withContext DeviceAuthorizeResult.Error("Server URL not configured")
                val cleanUrl = cleanUrl(serverUrl)
                val url = "$cleanUrl/api/client/auth/device/authorize"

                val deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})"
                val json = gson.toJson(DeviceAuthorizeRequest(deviceInfo))
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.use { it.body?.string() } ?: return@withContext DeviceAuthorizeResult.Error("Empty response")

                if (!response.isSuccessful) {
                    val errorMsg = try {
                        gson.fromJson(responseBody, DeviceErrorResponse::class.java)?.error
                    } catch (_: Exception) { null }
                    Log.e("Device authorize failed: HTTP ${response.code}: $responseBody")
                    return@withContext DeviceAuthorizeResult.Error(errorMsg ?: "HTTP ${response.code}")
                }

                val authResponse = gson.fromJson(responseBody, DeviceAuthorizeResponse::class.java)
                if (authResponse != null) {
                    DeviceAuthorizeResult.Success(authResponse)
                } else {
                    DeviceAuthorizeResult.Error("Invalid response")
                }
            } catch (e: Exception) {
                Log.e("Device authorize failed", e)
                DeviceAuthorizeResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    suspend fun devicePollToken(context: Context, deviceCode: String): DeviceTokenResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val serverUrl = getServerUrl(context) ?: return@withContext null
                val cleanUrl = cleanUrl(serverUrl)
                val url = "$cleanUrl/api/client/auth/device/token"

                val json = gson.toJson(DeviceTokenRequest(deviceCode))
                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.use { it.body?.string() } ?: return@withContext null

                gson.fromJson(responseBody, DeviceTokenResponse::class.java)
            } catch (e: Exception) {
                Log.e("Device token poll failed", e)
                null
            }
        }
    }

    fun saveDeviceAuthToken(context: Context, accessToken: String, refreshToken: String?, user: Any?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serverUrl = getServerUrl(context) ?: ""
        val editor = prefs.edit()
            .putString(KEY_TOKEN, accessToken)
            .putString(KEY_SERVER, cleanUrl(serverUrl))
            .putString(KEY_USER, gson.toJson(user))
        if (refreshToken != null) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        } else {
            editor.remove(KEY_REFRESH_TOKEN)
        }
        editor.apply()
    }

    private fun generateUrlSuffix(url: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            digest.update(url.toByteArray(Charsets.UTF_8))
            val messageDigest = digest.digest()
            val hexString = StringBuilder()
            for (b in messageDigest) {
                var h = Integer.toHexString(0xFF and b.toInt())
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
                val targetName = if (usedNames.contains(sub.name)) {
                    val suffixed = sub.name + "_" + generateUrlSuffix(sub.url)
                    if (usedNames.contains(suffixed)) {
                        // Rare: hash collision, append counter
                        var counter = 2
                        var candidate = "${suffixed}_$counter"
                        while (usedNames.contains(candidate) && counter < 100) {
                            counter++
                            candidate = "${suffixed}_$counter"
                        }
                        candidate
                    } else {
                        suffixed
                    }
                } else {
                    sub.name
                }
                usedNames.add(targetName)
                
                onProgress(context.getString(DesignR.string.format_fetching_configuration, targetName))

                // Find existing by Source (perfect match) or Name (fallback)
                val existing = currentProfiles.find { it.source == sub.url || it.name == targetName }

                if (existing != null) {
                    if (existing.source != sub.url || existing.name != targetName) {
                        patch(existing.uuid, targetName, sub.url, 0, null)
                        commit(existing.uuid)
                        update(existing.uuid)
                    }
                } else {
                    val uuid = create(com.github.kr328.clash.service.model.Profile.Type.Url, targetName, sub.url, null)
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

    // ── Update Check ──────────────────────────────────────────────

    sealed class UpdateResult {
        data class Available(val newVersion: String, val fileSize: Long, val downloadUrl: String) : UpdateResult()
        object UpToDate : UpdateResult()
        data class Error(val message: String) : UpdateResult()
    }

    fun isUpdateEnabled(): Boolean {
        return BuildConfig.UPDATE_ENABLED
            && BuildConfig.UPDATE_API_URL.isNotEmpty()
            && BuildConfig.UPDATE_APP_NAME.isNotEmpty()
    }

    suspend fun checkForUpdate(): UpdateResult {
        if (!isUpdateEnabled()) return UpdateResult.Error("disabled")

        return withContext(Dispatchers.IO) {
            try {
                val abi = android.os.Build.SUPPORTED_ABIS[0]
                val arch = when (abi) {
                    "arm64-v8a" -> "arm64"
                    "armeabi-v7a" -> "armv7"
                    "x86" -> "x86"
                    "x86_64" -> "x64"
                    else -> "universal"
                }
                val url = "${BuildConfig.UPDATE_API_URL}" +
                    "/api/download/latest" +
                    "?app=${BuildConfig.UPDATE_APP_NAME}" +
                    "&platform=android" +
                    "&arch=$arch" +
                    "&buildType=release"

                Log.i("Update: URL=$url, ABI=$abi")

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.use { it.body?.string() } ?: ""

                Log.i("Update: HTTP ${response.code}, body=${responseBody.take(300)}")

                if (!response.isSuccessful) {
                    return@withContext UpdateResult.Error("HTTP ${response.code}: ${responseBody.take(100)}")
                }

                val root = try {
                    com.google.gson.JsonParser.parseString(responseBody)
                } catch (e: Exception) {
                    Log.w("Update: JSON parse error", e)
                    return@withContext UpdateResult.Error("parse: ${e.message}")
                }

                if (!root.isJsonObject) {
                    return@withContext UpdateResult.Error("not a JSON object: ${responseBody.take(80)}")
                }
                val obj = root.asJsonObject

                val versionObj = obj.getAsJsonObject("version")
                if (versionObj == null) {
                    return@withContext UpdateResult.Error("no 'version' field: ${responseBody.take(200)}")
                }

                val latestVersion = versionObj.get("version")?.asString
                val fileSize = versionObj.get("fileSize")?.asLong ?: 0L
                val downloadUrl = obj.get("downloadUrl")?.asString

                if (latestVersion == null || downloadUrl == null) {
                    return@withContext UpdateResult.Error(
                        "version=${latestVersion}, downloadUrl=${downloadUrl}"
                    )
                }

                if (isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) {
                    val fullUrl = if (downloadUrl.startsWith("http")) downloadUrl
                        else "${BuildConfig.UPDATE_API_URL}$downloadUrl"
                    UpdateResult.Available(latestVersion, fileSize, fullUrl)
                } else {
                    UpdateResult.UpToDate
                }
            } catch (e: Exception) {
                Log.e("Update: exception", e)
                UpdateResult.Error("${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
