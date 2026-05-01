package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider

class SubLinksStore(context: Context) {
    private val store = Store(
        context.getSharedPreferences("sublinks_prefs", Context.MODE_PRIVATE)
            .asStoreProvider()
    )

    var autoSync by store.boolean(
        key = "auto_sync_startup",
        defaultValue = false
    )

    var heroBackgroundType by store.string(
        key = "hero_background_type",
        defaultValue = "network"
    )

    var heroNetworkUrl by store.string(
        key = "hero_network_url",
        defaultValue = DEFAULT_BACKGROUND_URL
    )

    var heroLocalUri by store.string(
        key = "hero_local_uri",
        defaultValue = ""
    )

    var heroColorCode by store.string(
        key = "hero_color_code",
        defaultValue = "#FF0000"
    )

    var skippedUpdateVersion by store.string(
        key = "skipped_update_version",
        defaultValue = ""
    )

    companion object {
        const val DEFAULT_BACKGROUND_URL = "https://www.loliapi.com/acg/"
    }
}
