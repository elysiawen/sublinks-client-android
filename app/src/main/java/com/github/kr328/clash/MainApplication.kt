package com.github.kr328.clash

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.design.store.UiStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.github.kr328.clash.common.compat.currentProcessName
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.util.clashDir
import java.io.File
import java.io.FileOutputStream
import com.github.kr328.clash.R
import com.github.kr328.clash.design.R as DesignR

@Suppress("unused")
class MainApplication : Application(), ImageLoaderFactory {

    companion object {
        private var heartbeatJob: Job? = null
        private val defaultIntervalMs = BuildConfig.HEARTBEAT_INTERVAL * 1000L

        fun startHeartbeat() {
            val app = Global.application as? MainApplication ?: return
            app.startHeartbeatInternal()
        }

        fun stopHeartbeat() {
            heartbeatJob?.cancel()
            heartbeatJob = null
            Log.d("Heartbeat stopped")
        }
    }

    private fun startHeartbeatInternal() {
        if (!SubLinksService.isLoggedIn(this)) {
            Log.d("Heartbeat: not logged in, skipping")
            return
        }
        if (!SubLinksService.isHeartbeatEnabled()) {
            Log.d("Heartbeat: disabled by config, skipping")
            return
        }

        if (heartbeatJob?.isActive == true) {
            Log.d("Heartbeat: already running")
            return
        }

        heartbeatJob?.cancel()
        heartbeatJob = Global.launch {
            Log.d("Heartbeat started, default interval=${BuildConfig.HEARTBEAT_INTERVAL}s")
            var intervalMs = defaultIntervalMs

            while (isActive) {
                try {
                    val response = SubLinksService.sendHeartbeat(this@MainApplication)
                    if (response != null && response.success && response.next_heartbeat_interval != null) {
                        intervalMs = response.next_heartbeat_interval * 1000L
                    } else {
                        // Server didn't return interval or request failed, keep using current/default
                        intervalMs = defaultIntervalMs
                    }
                } catch (_: Exception) {
                    // Network error, reset to default
                    intervalMs = defaultIntervalMs
                }
                delay(intervalMs)
            }
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        Global.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        val processName = currentProcessName

        Global.launch {
            extractGeoFiles()
        }

        Log.d("Process $processName started")

        if (processName == packageName) {
            Remote.launch()

            // Start heartbeat if user is already logged in
            if (SubLinksService.isLoggedIn(this)) {
                startHeartbeat()
            }
        } else {
            sendServiceRecreated()
        }
    }

    private fun setupShortcuts() {
        val uiStore = UiStore(this)
        if (uiStore.hideAppIcon) {
            // Prevent launcher activity not found.
            ShortcutManagerCompat.removeAllDynamicShortcuts(this)
            return
        }

        val icon = IconCompat.createWithResource(this, R.mipmap.ic_launcher)
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
            Intent.FLAG_ACTIVITY_NO_ANIMATION

        val toggle = ShortcutInfoCompat.Builder(this, "toggle_clash")
            .setShortLabel(getString(DesignR.string.shortcut_toggle_short))
            .setLongLabel(getString(DesignR.string.shortcut_toggle_long))
            .setIcon(icon)
            .setIntent(
                Intent(Intents.ACTION_TOGGLE_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(0)
            .build()

        val start = ShortcutInfoCompat.Builder(this, "start_clash")
            .setShortLabel(getString(DesignR.string.shortcut_start_short))
            .setLongLabel(getString(DesignR.string.shortcut_start_long))
            .setIcon(icon)
            .setIntent(
                Intent(Intents.ACTION_START_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(1)
            .build()

        val stop = ShortcutInfoCompat.Builder(this, "stop_clash")
            .setShortLabel(getString(DesignR.string.shortcut_stop_short))
            .setLongLabel(getString(DesignR.string.shortcut_stop_long))
            .setIcon(icon)
            .setIntent(
                Intent(Intents.ACTION_STOP_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(2)
            .build()

        ShortcutManagerCompat.setDynamicShortcuts(this, listOf(toggle, start, stop))
    }

    private fun extractGeoFiles() {
        clashDir.mkdirs()

        val updateDate = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        val geoipFile = File(clashDir, "geoip.metadb")
        if (geoipFile.exists() && geoipFile.lastModified() < updateDate) {
            geoipFile.delete()
        }
        if (!geoipFile.exists()) {
            FileOutputStream(geoipFile).use {
                assets.open("geoip.metadb").copyTo(it)
            }
        }

        val geositeFile = File(clashDir, "geosite.dat")
        if (geositeFile.exists() && geositeFile.lastModified() < updateDate) {
            geositeFile.delete()
        }
        if (!geositeFile.exists()) {
            FileOutputStream(geositeFile).use {
                assets.open("geosite.dat").copyTo(it)
            }
        }

        val asnFile = File(clashDir, "ASN.mmdb")
        if (asnFile.exists() && asnFile.lastModified() < updateDate) {
            asnFile.delete()
        }
        if (!asnFile.exists()) {
            FileOutputStream(asnFile).use {
                assets.open("ASN.mmdb").copyTo(it)
            }
        }

        val bundleMRSFile = File(clashDir, "BundleMRS.7z")
        if (bundleMRSFile.exists() && bundleMRSFile.lastModified() < updateDate) {
            bundleMRSFile.delete()
        }
        if (!bundleMRSFile.exists()) {
            FileOutputStream(bundleMRSFile).use {
                assets.open("BundleMRS.7z").copyTo(it)
            }
        }
    }

}
