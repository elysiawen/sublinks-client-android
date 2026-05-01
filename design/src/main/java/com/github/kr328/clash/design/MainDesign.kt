package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.load

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    enum class Request {
        ToggleStatus,
        OpenProxy,
        OpenProfiles,
        OpenProviders,
        OpenSettings,
        Logout,
        RefreshImage,
        OpenScan,
        SkipUpdate,
        DownloadUpdate,
    }

    private val binding = DesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
            binding.profileName = name
        }
    }

    suspend fun setUsername(name: String?) {
        withContext(Dispatchers.Main) {
            binding.username = name
        }
    }


    suspend fun setAvatar(url: String?) {
        withContext(Dispatchers.Main) {
            binding.avatarUrl = url
            binding.selfAvatar.load(url) {
                crossfade(true)
            }
        }
    }

    suspend fun setWelcomeMessage(text: String?) {
        withContext(Dispatchers.Main) {
            binding.welcomeMessage = text
        }
    }

    suspend fun setHitokoto(text: String?) {
        withContext(Dispatchers.Main) {
            binding.hitokoto = text
        }
    }

    suspend fun setHeroImage(bitmap: android.graphics.Bitmap) {
        withContext(Dispatchers.Main) {
            binding.heroImage.setImageBitmap(bitmap)
            binding.heroImage.alpha = 1f
        }
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            binding.clashRunning = running
        }
    }

    suspend fun setForwarded(value: Long) {
        withContext(Dispatchers.Main) {
            binding.forwarded = value.trafficTotal()
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            binding.mode = when (mode) {
                TunnelState.Mode.Direct -> context.getString(R.string.direct_mode)
                TunnelState.Mode.Global -> context.getString(R.string.global_mode)
                TunnelState.Mode.Rule -> context.getString(R.string.rule_mode)
                else -> context.getString(R.string.rule_mode)
            }
        }
    }

    suspend fun setHasProviders(has: Boolean) {
        withContext(Dispatchers.Main) {
            binding.hasProviders = has
        }
    }

    suspend fun showAbout(versionName: String) {
        withContext(Dispatchers.Main) {
            val binding = DesignAboutBinding.inflate(context.layoutInflater).apply {
                this.versionName = versionName
            }

            AlertDialog.Builder(context)
                .setView(binding.root)
                .show()
        }
    }

    suspend fun updateCardVisibility(
        showCard: Boolean,
        showAvatar: Boolean,
        showWelcome: Boolean,
        showHitokoto: Boolean,
        showRefresh: Boolean
    ) {
        withContext(Dispatchers.Main) {
             binding.showMainCard = showCard
             binding.showMainCardAvatar = showAvatar
             binding.showMainCardWelcome = showWelcome
             binding.showMainCardHitokoto = showHitokoto
             binding.showMainCardRefresh = showRefresh
             binding.executePendingBindings()
        }
    }

    private var _updateDownloadUrl: String = ""
    private var _updateVersion: String = ""

    suspend fun showUpdateCard(version: String, size: String, downloadUrl: String) {
        withContext(Dispatchers.Main) {
            binding.showUpdateCard = true
            binding.updateVersion = context.getString(R.string.update_available_short, version)
            binding.updateSize = size
            _updateDownloadUrl = downloadUrl
            _updateVersion = version
            binding.executePendingBindings()
        }
    }

    suspend fun hideUpdateCard() {
        withContext(Dispatchers.Main) {
            binding.showUpdateCard = false
            binding.executePendingBindings()
        }
    }

    fun getStoredDownloadUrl(): String = _updateDownloadUrl
    fun getStoredVersion(): String = _updateVersion

    init {
        binding.self = this

        binding.colorClashStarted = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        binding.colorClashStopped = context.resolveThemedColor(R.attr.colorClashStopped)
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}