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

class MainActivity : BaseActivity<MainDesign>() {
    override suspend fun main() {
        // SubLinks Login Check
        if (!SubLinksService.isLoggedIn(this)) {
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val design = MainDesign(this)

        setContentDesign(design)

        // Initialize Hero Card
        design.setUsername(SubLinksService.getUsername(this))
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
                      } else {
                          // Just validate token
                          SubLinksService.fetchSubscriptions(this@MainActivity)
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
                        MainDesign.Request.Logout -> {
                            withProfile {
                                try {
                                    queryAll().forEach { delete(it.uuid) }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            SubLinksService.logout(this@MainActivity)
                            startActivity(android.content.Intent(this@MainActivity, LoginActivity::class.java))
                            finish()
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

    private suspend fun MainDesign.updateHeroImage(force: Boolean = false) {
        val store = com.github.kr328.clash.service.store.SubLinksStore(this@MainActivity)
        val type = store.heroBackgroundType
        val value = when(type) {
             "network", "url", "api" -> store.heroNetworkUrl
             "local" -> store.heroLocalUri
             "color" -> store.heroColorCode
             else -> ""
        }
        val currentProps = type to value

        if (force || currentProps != lastHeroProps) {
            lastHeroProps = currentProps
            val bitmap = SubLinksService.fetchRandomImage(this@MainActivity)
            if (bitmap != null) {
                setHeroImage(bitmap)
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

val mainActivityAlias = "${MainActivity::class.java.name}Alias"