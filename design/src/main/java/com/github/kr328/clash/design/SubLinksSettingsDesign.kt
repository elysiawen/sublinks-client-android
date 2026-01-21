package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.common.compat.getDrawableCompat
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.preference.*
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.store.SubLinksStore

class SubLinksSettingsDesign(
    context: Context,
    store: SubLinksStore
) : Design<SubLinksSettingsDesign.Request>(context) {

    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.surface = surface

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        val screen = preferenceScreen(context) {
            switch(
                value = store::autoSync,
                icon = R.drawable.ic_baseline_sync,
                title = R.string.auto_sync_startup,
                summary = R.string.auto_sync_startup_summary,
            )
            category(R.string.personalization)

            val uiStore = com.github.kr328.clash.design.store.UiStore(context)
            var updateAllVisibility: () -> Unit = {}

            val sMainCard = switch(
                value = uiStore::showMainCard,
                title = R.string.main_card_visible,
                icon = R.drawable.ic_baseline_view_list
            ) {
                 listener = OnChangedListener { updateAllVisibility() }
            }

            val bgTypePref = selectableList(
                value = store::heroBackgroundType,
                values = arrayOf("network", "local", "color"),
                valuesText = arrayOf(
                    R.string.bg_type_network,
                    R.string.bg_type_local,
                    R.string.bg_type_color
                ),
                icon = R.drawable.ic_baseline_apps,
                title = R.string.hero_background_type
            ) {
                listener = OnChangedListener {
                    updateAllVisibility()
                }
            }

            val textPref = editableText(
                value = store::heroNetworkUrl,
                adapter = object : NullableTextAdapter<String> {
                    override fun from(value: String): String = value
                    override fun to(text: String?): String = text ?: ""
                },
                title = R.string.hero_background_value,
                placeholder = R.string.hero_background_value_hint,
                icon = R.drawable.ic_baseline_edit
            ) 

            val browsePref = clickable(
                title = R.string.browse_image,
                summary = R.string.browse_image_summary,
                icon = R.drawable.ic_outline_folder,
            ) {
                clicked {
                    requests.trySend(Request.PickImage)
                }
            }

            val blurPref = seekBar(
                value = uiStore::mainCardBlurRadius,
                title = R.string.main_card_blur_radius,
                summary = R.string.main_card_blur_summary,
                min = 0,
                max = 25,
                icon = R.drawable.ic_baseline_edit
            )

            val sAvatar = switch(
                value = uiStore::showMainCardAvatar,
                title = R.string.main_card_avatar_visible,
                icon = R.drawable.ic_baseline_person
            )

            val sWelcome = switch(
                value = uiStore::showMainCardWelcome,
                title = R.string.main_card_welcome_visible,
                icon = R.drawable.ic_baseline_info
            )

            val sHitokoto = switch(
                value = uiStore::showMainCardHitokoto,
                title = R.string.main_card_hitokoto_visible,
                icon = R.drawable.ic_outline_article
            )

            val sRefresh = switch(
                value = uiStore::showMainCardRefresh,
                title = R.string.main_card_refresh_visible,
                icon = R.drawable.ic_baseline_sync
            )

            updateAllVisibility = {
                val showCard = uiStore.showMainCard
                val cardVisibility = if (showCard) View.VISIBLE else View.GONE
                
                bgTypePref.view.visibility = cardVisibility
                blurPref.view.visibility = cardVisibility
                sAvatar.view.visibility = cardVisibility
                sWelcome.view.visibility = cardVisibility
                sHitokoto.view.visibility = cardVisibility
                sRefresh.view.visibility = cardVisibility
                
                if (showCard) {
                    val type = store.heroBackgroundType
                    when (type) {
                        "local" -> {
                            textPref.view.visibility = View.GONE
                            browsePref.view.visibility = View.VISIBLE
                            browsePref.title = context.getText(R.string.browse_image)
                            browsePref.summary = context.getText(R.string.browse_image_summary)
                            browsePref.icon = context.getDrawableCompat(R.drawable.ic_outline_folder)
                            browsePref.clicked { requests.trySend(Request.PickImage) }
                        }
                        "color" -> {
                            textPref.view.visibility = View.GONE
                            browsePref.view.visibility = View.VISIBLE
                            browsePref.title = context.getText(R.string.bg_value_color_hint)
                            val colorCode = store.heroColorCode
                            browsePref.summary = if (colorCode.isEmpty()) context.getText(R.string.enter_color_code) else colorCode
                            browsePref.icon = context.getDrawableCompat(R.drawable.ic_baseline_edit)
                            browsePref.clicked { requests.trySend(Request.PickColor) }
                        }
                        else -> { // network, api, url
                            textPref.view.visibility = View.VISIBLE
                            textPref.title = context.getText(R.string.bg_value_network_hint)
                            textPref.icon = context.getDrawableCompat(R.drawable.ic_baseline_dns)
                            browsePref.view.visibility = View.GONE
                        }
                    }
                } else {
                    textPref.view.visibility = View.GONE
                    browsePref.view.visibility = View.GONE
                }
            }
            
            updateAllVisibility()

        }

        binding.content.addView(screen.root)
    }
    
    sealed interface Request {
        object PickImage : Request
        object PickColor : Request
    }
    
    
}
