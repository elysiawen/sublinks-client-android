package com.github.kr328.clash

import android.content.Intent
import android.widget.Toast
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.SettingsDesign
import com.github.kr328.clash.design.R as DesignR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class SettingsActivity : BaseActivity<SettingsDesign>() {
    override suspend fun main() {
        val design = SettingsDesign(this)

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                design.requests.onReceive {
                    when (it) {
                        SettingsDesign.Request.StartApp ->
                            startActivity(AppSettingsActivity::class.intent)
                        SettingsDesign.Request.StartSubLinks ->
                            startActivity(SubLinksSettingsActivity::class.intent)
                        SettingsDesign.Request.StartNetwork ->
                            startActivity(NetworkSettingsActivity::class.intent)
                        SettingsDesign.Request.StartOverride ->
                            startActivity(OverrideSettingsActivity::class.intent)
                        SettingsDesign.Request.StartMetaFeature ->
                            startActivity(MetaFeatureSettingsActivity::class.intent)
                        SettingsDesign.Request.StartLogs -> {
                            if (LogcatService.running) {
                                startActivity(LogcatActivity::class.intent)
                            } else {
                                startActivity(LogsActivity::class.intent)
                            }
                        }
                        SettingsDesign.Request.StartHelp ->
                            startActivity(HelpActivity::class.intent)
                        SettingsDesign.Request.StartAbout ->
                            design.showAbout(queryAppVersionName())
                        SettingsDesign.Request.CheckUpdate -> {
                            val activity = this@SettingsActivity
                            activity.launch {
                                try {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(activity, DesignR.string.update_checking, Toast.LENGTH_SHORT).show()
                                    }
                                    val result = SubLinksService.checkForUpdate()
                                    if (!activity.isActive) return@launch
                                    withContext(Dispatchers.Main) {
                                        when (result) {
                                            is SubLinksService.UpdateResult.Available -> {
                                                val sizeMB = result.fileSize / 1024.0 / 1024.0
                                                val message = activity.getString(DesignR.string.update_available, result.newVersion, sizeMB)
                                                androidx.appcompat.app.AlertDialog.Builder(activity)
                                                    .setTitle(DesignR.string.update_new_version_title)
                                                    .setMessage(message)
                                                    .setPositiveButton(DesignR.string.update_download) { _, _ ->
                                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(result.downloadUrl))
                                                        activity.startActivity(intent)
                                                    }
                                                    .setNegativeButton(DesignR.string.login_2fa_cancel, null)
                                                    .show()
                                            }
                                            is SubLinksService.UpdateResult.UpToDate -> {
                                                Toast.makeText(activity, DesignR.string.update_up_to_date, Toast.LENGTH_LONG).show()
                                            }
                                            is SubLinksService.UpdateResult.Error -> {
                                                Toast.makeText(activity, getString(DesignR.string.update_check_failed, result.message), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(activity, getString(DesignR.string.update_check_failed, e.message ?: "Unknown"), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun queryAppVersionName(): String {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName + "\n" + com.github.kr328.clash.core.bridge.Bridge.nativeCoreVersion().replace("_", "-")
        }
    }
}