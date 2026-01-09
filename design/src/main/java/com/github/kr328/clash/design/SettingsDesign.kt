package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignSettingsBinding
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class SettingsDesign(context: Context) : Design<SettingsDesign.Request>(context) {
    enum class Request {
        StartApp, StartSubLinks, StartNetwork, StartOverride, StartMetaFeature,
        StartLogs, StartHelp, StartAbout
    }

    private val binding = DesignSettingsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)
    }

    fun request(request: Request) {
        requests.trySend(request)
    }

    suspend fun showAbout(versionName: String) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            val binding = com.github.kr328.clash.design.databinding.DesignAboutBinding.inflate(context.layoutInflater).apply {
                this.versionName = versionName
            }

            androidx.appcompat.app.AlertDialog.Builder(context)
                .setView(binding.root)
                .show()
        }
    }
}