package com.github.kr328.clash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.kr328.clash.R
import com.github.kr328.clash.design.R as DesignR

class LoginActivity : AppCompatActivity() {

    private var devicePollJob: Job? = null
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val tabLayout = findViewById<TabLayout>(R.id.tab_login_method)
        val browserContainer = findViewById<LinearLayout>(R.id.container_browser_login)
        val passwordContainer = findViewById<LinearLayout>(R.id.container_password_login)

        // Setup tabs
        tabLayout.addTab(tabLayout.newTab().setText(DesignR.string.login_method_browser))
        tabLayout.addTab(tabLayout.newTab().setText(DesignR.string.login_method_password))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.position == 0) {
                    browserContainer.visibility = View.VISIBLE
                    passwordContainer.visibility = View.GONE
                } else {
                    browserContainer.visibility = View.GONE
                    passwordContainer.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        setupBrowserLogin()
        setupPasswordLogin()
    }

    override fun onDestroy() {
        super.onDestroy()
        devicePollJob?.cancel()
        countDownTimer?.cancel()
    }

    // ── Browser Login (Device Code Flow) ──────────────────────

    private fun setupBrowserLogin() {
        val btnBrowserLogin = findViewById<MaterialButton>(R.id.btn_browser_login)
        val btnBrowserCancel = findViewById<MaterialButton>(R.id.btn_browser_cancel)

        btnBrowserLogin.setOnClickListener {
            startDeviceCodeFlow()
        }

        btnBrowserCancel.setOnClickListener {
            cancelDeviceCodeFlow()
        }
    }

    private fun startDeviceCodeFlow() {
        val btnLogin = findViewById<MaterialButton>(R.id.btn_browser_login)
        val tvDesc = findViewById<TextView>(R.id.tv_browser_desc)
        val ivIcon = findViewById<View>(R.id.iv_browser_icon)
        val containerPolling = findViewById<LinearLayout>(R.id.container_polling)
        val tvStatus = findViewById<TextView>(R.id.tv_browser_status)
        val tvCountdown = findViewById<TextView>(R.id.tv_browser_countdown)
        val progressPolling = findViewById<ProgressBar>(R.id.progress_polling)

        devicePollJob?.cancel()
        countDownTimer?.cancel()

        devicePollJob = lifecycleScope.launch {
            btnLogin.isEnabled = false
            btnLogin.text = getString(DesignR.string.login_browser_opening)

            val server = SubLinksService.getServerUrl(this@LoginActivity) ?: ""
            if (server.isEmpty()) {
                Toast.makeText(this@LoginActivity, "Server URL not configured", Toast.LENGTH_SHORT).show()
                btnLogin.isEnabled = true
                btnLogin.setText(DesignR.string.login_browser_button)
                return@launch
            }

            val authResult = SubLinksService.deviceAuthorize(this@LoginActivity)

            if (authResult is SubLinksService.DeviceAuthorizeResult.Error) {
                Toast.makeText(this@LoginActivity, getString(DesignR.string.login_browser_error, authResult.message), Toast.LENGTH_LONG).show()
                btnLogin.isEnabled = true
                btnLogin.setText(DesignR.string.login_browser_button)
                return@launch
            }

            val authResponse = (authResult as SubLinksService.DeviceAuthorizeResult.Success).response

            // Open browser
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authResponse.verificationUri))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Cannot open browser", Toast.LENGTH_SHORT).show()
                btnLogin.isEnabled = true
                btnLogin.setText(DesignR.string.login_browser_button)
                return@launch
            }

            // Show polling UI
            btnLogin.visibility = View.GONE
            tvDesc.visibility = View.GONE
            ivIcon.visibility = View.GONE
            containerPolling.visibility = View.VISIBLE
            tvStatus.text = getString(DesignR.string.login_browser_waiting)
            tvCountdown.text = ""

            // Start countdown
            val expiresInMillis = authResponse.expiresIn * 1000L
            countDownTimer = object : CountDownTimer(expiresInMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secondsLeft = (millisUntilFinished / 1000).toInt()
                    tvCountdown.text = getString(DesignR.string.login_browser_countdown, secondsLeft)
                }
                override fun onFinish() {
                    tvCountdown.text = ""
                }
            }.start()

            // Poll for token
            val intervalMs = (authResponse.interval * 1000).toLong()
            val expiresAt = System.currentTimeMillis() + expiresInMillis

            while (isActive && System.currentTimeMillis() < expiresAt) {
                withContext(Dispatchers.IO) {
                    try {
                        Thread.sleep(intervalMs)
                    } catch (_: InterruptedException) {
                        return@withContext
                    }
                }

                if (!isActive) break

                val pollResult = SubLinksService.devicePollToken(this@LoginActivity, authResponse.deviceCode)

                if (pollResult == null) {
                    // Network error, continue polling
                    continue
                }

                when {
                    pollResult.success == true && pollResult.accessToken != null -> {
                        // Success! Save tokens
                        countDownTimer?.cancel()
                        SubLinksService.saveDeviceAuthToken(
                            this@LoginActivity,
                            pollResult.accessToken,
                            pollResult.refreshToken,
                            pollResult.user
                        )
                        handleLoginSuccess()
                        return@launch
                    }
                    pollResult.error == "authorization_pending" -> {
                        // Still waiting, continue
                        continue
                    }
                    pollResult.error == "authorization_denied" -> {
                        countDownTimer?.cancel()
                        withContext(Dispatchers.Main) {
                            resetBrowserUI()
                            Toast.makeText(this@LoginActivity, getString(DesignR.string.login_browser_denied), Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    else -> {
                        countDownTimer?.cancel()
                        withContext(Dispatchers.Main) {
                            resetBrowserUI()
                            Toast.makeText(this@LoginActivity, getString(DesignR.string.login_browser_error, pollResult.error ?: "Unknown"), Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                }
            }

            // Expired
            countDownTimer?.cancel()
            withContext(Dispatchers.Main) {
                resetBrowserUI()
                Toast.makeText(this@LoginActivity, getString(DesignR.string.login_browser_expired), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cancelDeviceCodeFlow() {
        devicePollJob?.cancel()
        countDownTimer?.cancel()
        devicePollJob = null
        resetBrowserUI()
    }

    private fun resetBrowserUI() {
        val btnLogin = findViewById<MaterialButton>(R.id.btn_browser_login)
        val tvDesc = findViewById<TextView>(R.id.tv_browser_desc)
        val ivIcon = findViewById<View>(R.id.iv_browser_icon)
        val containerPolling = findViewById<LinearLayout>(R.id.container_polling)
        val progressPolling = findViewById<ProgressBar>(R.id.progress_polling)

        btnLogin.visibility = View.VISIBLE
        btnLogin.isEnabled = true
        btnLogin.setText(DesignR.string.login_browser_button)
        tvDesc.visibility = View.VISIBLE
        ivIcon.visibility = View.VISIBLE
        containerPolling.visibility = View.GONE
        progressPolling.visibility = View.VISIBLE
    }

    // ── Password Login ────────────────────────────────────────

    private fun setupPasswordLogin() {
        val editUsername = findViewById<TextInputEditText>(R.id.edit_username)
        val editPassword = findViewById<TextInputEditText>(R.id.edit_password)
        val btnLogin = findViewById<MaterialButton>(R.id.btn_login)

        btnLogin.setOnClickListener {
            val username = editUsername.text.toString()
            val password = editPassword.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(DesignR.string.login_fill_all), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                btnLogin.isEnabled = false
                btnLogin.text = getString(DesignR.string.loading)

                val server = SubLinksService.getServerUrl(this@LoginActivity) ?: ""

                val result = SubLinksService.login(this@LoginActivity, server, username, password)

                when (result) {
                    is SubLinksService.LoginResult.Success -> {
                        handleLoginSuccess()
                    }
                    is SubLinksService.LoginResult.Requires2FA -> {
                        btnLogin.isEnabled = true
                        btnLogin.setText(DesignR.string.login_button)

                        val input = TextInputEditText(this@LoginActivity).apply {
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER
                            hint = getString(DesignR.string.login_2fa_hint)
                            maxLines = 1
                        }
                        val container = android.widget.FrameLayout(this@LoginActivity).apply {
                            val params = android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                            )
                            val margin = (24 * resources.displayMetrics.density).toInt()
                            params.setMargins(margin, margin / 4, margin, margin / 4)
                            input.layoutParams = params
                            addView(input)
                        }

                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this@LoginActivity)
                            .setTitle(DesignR.string.login_2fa_title)
                            .setMessage(result.message)
                            .setView(container)
                            .setPositiveButton(DesignR.string.login_2fa_confirm) { _, _ ->
                                val code = input.text.toString()
                                if (code.isEmpty()) return@setPositiveButton

                                lifecycleScope.launch {
                                    btnLogin.isEnabled = false
                                    btnLogin.text = getString(DesignR.string.loading)
                                    val secondResult = SubLinksService.login(this@LoginActivity, server, username, password, code)
                                    if (secondResult is SubLinksService.LoginResult.Success) {
                                        handleLoginSuccess()
                                    } else {
                                        val errorMsg = if (secondResult is SubLinksService.LoginResult.Error) secondResult.message else (secondResult as SubLinksService.LoginResult.Requires2FA).message
                                        Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                                        btnLogin.isEnabled = true
                                        btnLogin.setText(DesignR.string.login_button)
                                    }
                                }
                            }
                            .setNegativeButton(DesignR.string.login_2fa_cancel, null)
                            .show()

                        input.requestFocus()
                    }
                    is SubLinksService.LoginResult.Error -> {
                        Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_LONG).show()
                        btnLogin.isEnabled = true
                        btnLogin.setText(DesignR.string.login_button)
                    }
                }
            }
        }
    }

    // ── Common ────────────────────────────────────────────────

    private suspend fun handleLoginSuccess() {
        val cardLogin = findViewById<View>(R.id.card_login)
        val cardSync = findViewById<View>(R.id.card_sync)
        val tvSyncStatus = findViewById<TextView>(R.id.tv_sync_status)
        val tvSyncDetail = findViewById<TextView>(R.id.tv_sync_detail)

        withContext(Dispatchers.Main) {
            // Hide login card, show sync card
            cardLogin.visibility = View.GONE
            cardSync.visibility = View.VISIBLE
            tvSyncStatus.text = getString(DesignR.string.syncing_subscriptions)
            tvSyncDetail.text = ""
        }

        try {
            SubLinksService.sync(this@LoginActivity) { msg ->
                withContext(Dispatchers.Main) {
                    tvSyncDetail.text = msg
                }
            }
            withContext(Dispatchers.Main) {
                tvSyncStatus.text = getString(DesignR.string.sync_completed)
                tvSyncDetail.text = ""
                Toast.makeText(this@LoginActivity, getString(DesignR.string.format_welcome, SubLinksService.getUsername(this@LoginActivity) ?: ""), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                tvSyncStatus.text = getString(DesignR.string.sync_failed, e.message ?: "")
                Toast.makeText(this@LoginActivity, getString(DesignR.string.sync_failed_login_tip, e.message), Toast.LENGTH_LONG).show()
            }
        }

        withContext(Dispatchers.Main) {
            // Start heartbeat after successful login
            MainApplication.startHeartbeat()

            val intent = Intent(this@LoginActivity, MainActivity::class.java)
            intent.putExtra("skip_sync", true)
            startActivity(intent)
            finish()
        }
    }
}
