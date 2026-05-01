package com.github.kr328.clash

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.design.R as DesignR
import com.github.kr328.clash.R

class QrLoginConfirmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TOKEN = "token"
        const val EXTRA_IP = "ip"
        const val EXTRA_UA = "ua"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(DesignR.layout.activity_qr_confirm)

        val token = intent.getStringExtra(EXTRA_TOKEN) ?: run {
            finish()
            return
        }
        val ip = intent.getStringExtra(EXTRA_IP) ?: "Unknown IP"
        val ua = intent.getStringExtra(EXTRA_UA) ?: "Unknown Device"

        val textPlatform = findViewById<TextView>(DesignR.id.text_platform)
        val textLocation = findViewById<TextView>(DesignR.id.text_location)
        val textIp = findViewById<TextView>(DesignR.id.text_ip)
        val btnConfirm = findViewById<Button>(DesignR.id.btn_confirm)
        val btnCancel = findViewById<Button>(DesignR.id.btn_cancel)

        val platform = SubLinksService.parseUserAgent(ua)
        textPlatform.text = platform
        textIp.text = ip
        textLocation.text = "Loading..."

        lifecycleScope.launch {
            try {
                val ipInfo = SubLinksService.fetchIpInfo(ip)
                if (ipInfo != null && ipInfo.status == "success") {
                    val location = "${ipInfo.city}, ${ipInfo.regionName}"
                    val isp = ipInfo.isp
                    textLocation.text = "$location\n$isp"
                } else {
                    textLocation.text = "Unknown Location"
                }
            } catch (e: Exception) {
                textLocation.text = "Failed to load"
            }
        }

        btnConfirm.setOnClickListener {
            lifecycleScope.launch {
                val originalText = btnConfirm.text
                btnConfirm.isEnabled = false
                btnConfirm.text = getString(DesignR.string.scan_login_loading)
                (btnConfirm as? com.google.android.material.button.MaterialButton)?.icon = null
                try {
                    val success = SubLinksService.qrConfirm(this@QrLoginConfirmActivity, token)
                    if (success) {
                        Toast.makeText(this@QrLoginConfirmActivity, DesignR.string.scan_login_success, Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@QrLoginConfirmActivity, getString(DesignR.string.scan_login_failed, "Failed"), Toast.LENGTH_LONG).show()
                        btnConfirm.text = originalText
                        (btnConfirm as? com.google.android.material.button.MaterialButton)?.setIconResource(DesignR.drawable.ic_outline_check_circle)
                        btnConfirm.isEnabled = true
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@QrLoginConfirmActivity, getString(DesignR.string.scan_login_failed, e.message), Toast.LENGTH_LONG).show()
                    btnConfirm.text = originalText
                    (btnConfirm as? com.google.android.material.button.MaterialButton)?.setIconResource(DesignR.drawable.ic_outline_check_circle)
                    btnConfirm.isEnabled = true
                }
            }
        }

        btnCancel.setOnClickListener {
            lifecycleScope.launch {
                btnCancel.isEnabled = false
                try {
                    SubLinksService.qrReject(this@QrLoginConfirmActivity, token)
                } catch (e: Exception) {
                    Log.w("QR reject failed", e)
                } finally {
                    finish()
                }
            }
        }
    }

}
