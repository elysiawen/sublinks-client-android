package com.github.kr328.clash

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.kr328.clash.R
import com.github.kr328.clash.design.R as DesignR

class LoginActivity : AppCompatActivity() {
    private val scope = MainScope()

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

            scope.launch {
                btnLogin.isEnabled = false
                btnLogin.text = getString(DesignR.string.loading) // Use loading string or "Logging in..."

                // Use the configured URL directly
                val server = SubLinksService.getServerUrl(this@LoginActivity) ?: ""
                val error = SubLinksService.login(this@LoginActivity, server, username, password)

                if (error == null) {
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
                         Toast.makeText(this@LoginActivity, getString(DesignR.string.sync_failed, e.message), Toast.LENGTH_LONG).show()
                         // Still enter main activity? Or stay?
                         // User said "Download then enter". If failed, maybe enter anyway?
                         // But usually if failed, user might want to retry.
                         // Let's stay and re-enable button.
                         btnLogin.isEnabled = true
                         btnLogin.setText(DesignR.string.login_button)
                    }
                } else {
                    Toast.makeText(this@LoginActivity, error, Toast.LENGTH_LONG).show()
                    btnLogin.isEnabled = true
                    btnLogin.setText(DesignR.string.login_button)
                }
            }
        }
    }
}
