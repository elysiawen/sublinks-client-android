package com.github.kr328.clash

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.kr328.clash.R
import com.github.kr328.clash.design.R as DesignR

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val editUsername = findViewById<TextInputEditText>(R.id.edit_username)
        val editPassword = findViewById<TextInputEditText>(R.id.edit_password)
        val btnLogin = findViewById<Button>(R.id.btn_login)

        btnLogin.setOnClickListener {
            val username = editUsername.text.toString()
            val password = editPassword.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(DesignR.string.login_fill_all), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                btnLogin.isEnabled = false
                btnLogin.text = getString(DesignR.string.loading) // Use loading string or "Logging in..."

                // Use the configured URL directly
                val server = SubLinksService.getServerUrl(this@LoginActivity) ?: ""
                
                suspend fun handleLoginSuccess() {
                    btnLogin.text = getString(DesignR.string.initializing) // Initializing
                    
                    try {
                        SubLinksService.sync(this@LoginActivity) { msg ->
                            withContext(Dispatchers.Main) {
                                btnLogin.text = msg
                            }
                        }
                        Toast.makeText(this@LoginActivity, getString(DesignR.string.format_welcome, username), Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        intent.putExtra("skip_sync", true)
                        startActivity(intent)
                        finish()
                    } catch (e: Exception) {
                         Toast.makeText(this@LoginActivity, getString(DesignR.string.sync_failed_login_tip, e.message), Toast.LENGTH_LONG).show()
                         // Proceed to main activity even if sync fails, so user can retry manually
                         val intent = Intent(this@LoginActivity, MainActivity::class.java)
                         intent.putExtra("skip_sync", true)
                         startActivity(intent)
                         finish()
                    }
                }

                val result = SubLinksService.login(this@LoginActivity, server, username, password)

                when (result) {
                    is SubLinksService.LoginResult.Success -> {
                        handleLoginSuccess()
                    }
                    is SubLinksService.LoginResult.Requires2FA -> {
                        btnLogin.isEnabled = true
                        btnLogin.setText(DesignR.string.login_button)
                        
                        val input = com.google.android.material.textfield.TextInputEditText(this@LoginActivity).apply {
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
                            params.setMargins(margin, margin/4, margin, margin/4)
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
}
