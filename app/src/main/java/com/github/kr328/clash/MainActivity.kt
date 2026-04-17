package com.github.kr328.clash

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.core.bridge.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R
import kotlinx.coroutines.launch

import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode

class MainActivity : BaseActivity<MainDesign>() {
    private val scanQr = registerForActivityResult(ScanQRCode()) { result ->
        this@MainActivity.launch {
            when (result) {
                is QRResult.QRSuccess -> {
                    val content = result.content.rawValue ?: ""
                    // Scheme: sublinks://login/<token>
                    if (content.startsWith("sublinks://login/")) {
                        val token = content.removePrefix("sublinks://login/")
                        handleQrLogin(token)
                    } else {
                        design?.showToast(R.string.scan_login_invalid, ToastDuration.Short)
                    }
                }
                is QRResult.QRUserCanceled -> Unit // Do nothing
                is QRResult.QRMissingPermission -> design?.showToast(
                    R.string.import_from_qr_no_permission,
                    ToastDuration.Long
                )

                is Exception -> design?.showToast(
                    R.string.import_from_qr_exception,
                    ToastDuration.Long
                )

                else -> {}
            }
        }
    }

    private fun handleQrLogin(token: String) {
        launch {
            val loading = androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                .setView(android.widget.ProgressBar(this@MainActivity).apply { 
                    setPadding(50, 50, 50, 50) 
                })
                .setCancelable(false)
                .show()

            try {
                // 1. Scan (Notify server & get info)
                val info = SubLinksService.qrScan(this@MainActivity, token)
                loading.dismiss()

                if (info != null) {
                    // 2. Launch Confirmation Activity
                    val intent = android.content.Intent(this@MainActivity, QrLoginConfirmActivity::class.java).apply {
                        putExtra(QrLoginConfirmActivity.EXTRA_TOKEN, token)
                        putExtra(QrLoginConfirmActivity.EXTRA_IP, info.ip)
                        putExtra(QrLoginConfirmActivity.EXTRA_UA, info.ua)
                    }
                    startActivity(intent)
                } else {
                    design?.showToast(getString(R.string.scan_login_failed, "Unknown error"), ToastDuration.Long)
                }
            } catch (e: Exception) {
                loading.dismiss()
                e.printStackTrace()
                design?.showToast(getString(R.string.scan_login_failed, e.message), ToastDuration.Long)
            }
        }
    }

    private fun confirmQrLogin(token: String) {
        launch {
             try {
                 val success = SubLinksService.qrConfirm(this@MainActivity, token)
                 if (success) {
                     design?.showToast(R.string.scan_login_success, ToastDuration.Short)
                 } else {
                     design?.showToast(getString(R.string.scan_login_failed, "Failed"), ToastDuration.Long)
                 }
             } catch (e: Exception) {
                 design?.showToast(getString(R.string.scan_login_failed, e.message), ToastDuration.Long)
             }
        }
    }

    override suspend fun main() {
        // SubLinks Login Check
        if (!SubLinksService.isLoggedIn(this)) {
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val design = MainDesign(this)

        val uiStore = com.github.kr328.clash.design.store.UiStore(this)
        design.updateCardVisibility(
            uiStore.showMainCard,
            uiStore.showMainCardAvatar,
            uiStore.showMainCardWelcome,
            uiStore.showMainCardHitokoto,
            uiStore.showMainCardRefresh
        )

        setContentDesign(design)

        launch { design.updateHeroImage() }

        // Initialize Hero Card
        design.setUsername(SubLinksService.getUsername(this))
        design.setAvatar(SubLinksService.getAvatar(this))
        design.setWelcomeMessage(SubLinksService.getGreeting(this))

         launch {
              // Background Sync
              val skipSync = intent?.getBooleanExtra("skip_sync", false) ?: false
              val autoSync = com.github.kr328.clash.service.store.SubLinksStore(this@MainActivity).autoSync
              if (!skipSync) {
                  try {
                      if (autoSync) {
                          withContext(Dispatchers.Main) {
                              android.widget.Toast.makeText(this@MainActivity, getString(R.string.syncing_subscriptions), android.widget.Toast.LENGTH_SHORT).show()
                          }
                          SubLinksService.sync(this@MainActivity) { 
                               // Silent progress or log
                          }
                          withContext(Dispatchers.Main) {
                              android.widget.Toast.makeText(this@MainActivity, getString(R.string.sync_completed), android.widget.Toast.LENGTH_SHORT).show()
                          }
                      }
                      
                      // Refresh user info
                      if (SubLinksService.fetchUserInfo(this@MainActivity)) {
                          design.setUsername(SubLinksService.getUsername(this@MainActivity))
                          design.setAvatar(SubLinksService.getAvatar(this@MainActivity))
                      }
                  } catch (e: SubLinksService.AuthenticationException) {
                      withContext(Dispatchers.Main) {
                          android.widget.Toast.makeText(this@MainActivity, getString(R.string.token_expired), android.widget.Toast.LENGTH_LONG).show()
                      }
                      SubLinksService.logout(this@MainActivity)
                      withContext(Dispatchers.Main) {
                          startActivity(android.content.Intent(this@MainActivity, LoginActivity::class.java))
                          finish()
                      }
                  } catch (e: Exception) {
                      e.printStackTrace()
                      withContext(Dispatchers.Main) {
                          android.widget.Toast.makeText(this@MainActivity, getString(R.string.sync_failed, e.message), android.widget.Toast.LENGTH_LONG).show()
                      }
                  }
              }
              
              // Refresh UI after sync
             design.fetch()
        }

        launch {
             // Fetch Hitokoto
             val hitokoto = SubLinksService.fetchHitokoto()
             design.setHitokoto(hitokoto ?: getString(R.string.hitokoto_failed))
        }

        design.fetch()

        val ticker = ticker(TimeUnit.SECONDS.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart -> {
                            design.fetch()
                            launch { design.updateHeroImage(false) }
                        }
                        Event.ServiceRecreated,
                        Event.ClashStop, Event.ClashStart,
                        Event.ProfileLoaded, Event.ProfileChanged -> design.fetch()
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        MainDesign.Request.ToggleStatus -> {
                            if (clashRunning)
                                stopClashService()
                            else
                                design.startClash()
                        }
                        MainDesign.Request.OpenProxy ->
                            startActivity(ProxyActivity::class.intent)
                        MainDesign.Request.OpenProfiles ->
                            startActivity(ProfilesActivity::class.intent)
                        MainDesign.Request.OpenProviders ->
                            startActivity(ProvidersActivity::class.intent)

                        MainDesign.Request.OpenSettings ->
                            startActivity(SettingsActivity::class.intent)
                        MainDesign.Request.OpenScan ->
                            scanQr.launch(null)
                        MainDesign.Request.Logout -> {
                            androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                                .setTitle(R.string.logout_confirmation_title)
                                .setMessage(R.string.logout_confirmation_message)
                                .setPositiveButton(R.string.ok) { _, _ ->
                                    launch {
                                        withContext(Dispatchers.Main) {
                                             android.widget.Toast.makeText(this@MainActivity, R.string.logging_out, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        val (success, message) = SubLinksService.logout(this@MainActivity)
                                        withContext(Dispatchers.Main) {
                                            if (!message.isNullOrEmpty()) {
                                                android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                android.widget.Toast.makeText(this@MainActivity, R.string.logout_success, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            startActivity(android.content.Intent(this@MainActivity, LoginActivity::class.java))
                                            finish()
                                        }
                                    }
                                }
                                .setNegativeButton(R.string.cancel, null)
                                .show()
                        }
                        MainDesign.Request.RefreshImage -> {
                            launch {
                                design.updateHeroImage(true)
                            }
                        }
                    }
                }
                if (clashRunning) {
                    ticker.onReceive {
                        design.fetchTraffic()
                    }
                }
            }
        }
    }

    private suspend fun MainDesign.fetch() {
        setClashRunning(clashRunning)

        val state = withClash {
            queryTunnelState()
        }
        val providers = withClash {
            queryProviders()
        }

        setMode(state.mode)
        setHasProviders(providers.isNotEmpty())

        withProfile {
            setProfileName(queryActive()?.name)
        }
    }

    private suspend fun MainDesign.fetchTraffic() {
        withClash {
            setForwarded(queryTrafficTotal())
        }
    }

    private var lastHeroProps: Pair<String, String>? = null
    private var rawHeroBitmap: android.graphics.Bitmap? = null
    private var lastBlurRadius: Int = -1

    private suspend fun MainDesign.updateHeroImage(force: Boolean = false) {
        val store = com.github.kr328.clash.service.store.SubLinksStore(this@MainActivity)
        val uiStore = com.github.kr328.clash.design.store.UiStore(this@MainActivity)
        
        val type = store.heroBackgroundType
        val value = when(type) {
             "network", "url", "api" -> store.heroNetworkUrl
             "local" -> store.heroLocalUri
             "color" -> store.heroColorCode
             else -> ""
        }
        
        val currentProps = type to value
        val blurRadius = uiStore.mainCardBlurRadius
        
        val showMainCard = uiStore.showMainCard
        val showAvatar = uiStore.showMainCardAvatar
        val showWelcome = uiStore.showMainCardWelcome
        val showHitokoto = uiStore.showMainCardHitokoto
        val showRefresh = uiStore.showMainCardRefresh
        
        updateCardVisibility(showMainCard, showAvatar, showWelcome, showHitokoto, showRefresh)
        
        if (!showMainCard) return

        var bitmapChanged = false

        if (force || currentProps != lastHeroProps) {
            lastHeroProps = currentProps
            val fetched = SubLinksService.fetchRandomImage(this@MainActivity)
            if (fetched != null) {
                rawHeroBitmap = fetched
                bitmapChanged = true
            }
        }
        
        if (rawHeroBitmap != null) {
            if (bitmapChanged || blurRadius != lastBlurRadius) {
                lastBlurRadius = blurRadius
                val finalBitmap = if (blurRadius > 0) {
                     com.github.kr328.clash.util.BlurUtils.fastblur(rawHeroBitmap!!, 0.5f, blurRadius) ?: rawHeroBitmap!!
                } else {
                     rawHeroBitmap!!
                }
                setHeroImage(finalBitmap)
            }
        }
    }

    private suspend fun MainDesign.startClash() {
        val active = withProfile { queryActive() }

        if (active == null || !active.imported) {
            showToast(R.string.no_profile_selected, ToastDuration.Long) {
                setAction(R.string.profiles) {
                    startActivity(ProfilesActivity::class.intent)
                }
            }

            return
        }

        val vpnRequest = startClashService()

        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )

                if (result.resultCode == RESULT_OK)
                    startClashService()
            }
        } catch (e: Exception) {
            design?.showToast(R.string.unable_to_start_vpn, ToastDuration.Long)
        }
    }

    private suspend fun queryAppVersionName(): String {
        return withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName + "\n" + Bridge.nativeCoreVersion().replace("_", "-")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher =
                registerForActivityResult(RequestPermission()
                ) { isGranted: Boolean ->
                }
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
