package com.github.kr328.clash

import com.github.kr328.clash.design.SubLinksSettingsDesign
import com.github.kr328.clash.service.store.SubLinksStore
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.github.kr328.clash.design.R as DesignR

class SubLinksSettingsActivity : BaseActivity<SubLinksSettingsDesign>() {
    private val pickImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore failure to take persistable permission
                e.printStackTrace()
            }
            val store = SubLinksStore(this)
            store.heroLocalUri = uri.toString()
            store.heroBackgroundType = "local"
        }
    }

    override suspend fun main() {
        val store = SubLinksStore(this)
        val design = SubLinksSettingsDesign(
            this,
            store
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive { }
                design.requests.onReceive {
                    when (it) {
                        SubLinksSettingsDesign.Request.PickImage -> {
                            pickImage.launch(arrayOf("image/*"))
                        }
                        SubLinksSettingsDesign.Request.PickColor -> {
                            val context = this@SubLinksSettingsActivity
                            val flavors = arrayOf(
                                DesignR.string.color_solid_black to "#000000",
                                DesignR.string.color_solid_white to "#FFFFFF",
                                DesignR.string.color_solid_red to "#EF5350",
                                DesignR.string.color_solid_green to "#66BB6A",
                                DesignR.string.color_solid_blue to "#42A5F5",
                                DesignR.string.color_gradient_sunset to "gradient:#FF5F6D,#FFC371",
                                DesignR.string.color_gradient_ocean to "gradient:#2193b0,#6dd5ed",
                                DesignR.string.color_gradient_lush to "gradient:#56ab2f,#a8e063",
                                DesignR.string.color_gradient_cool_blues to "gradient:#2193b0,#6dd5ed",
                                DesignR.string.color_custom to "CUSTOM"
                            )
                            val names: Array<CharSequence> = flavors.map { getString(it.first) }.toTypedArray()
                            
                            withContext(Dispatchers.Main) {
                                androidx.appcompat.app.AlertDialog.Builder(context)
                                    .setTitle(DesignR.string.bg_type_color)
                                    .setItems(names) { _, which ->
                                        if (flavors[which].second == "CUSTOM") {
                                            // Show color picker dialog
                                            val layout = android.widget.LinearLayout(context)
                                            layout.orientation = android.widget.LinearLayout.VERTICAL
                                            val padding = (24 * context.resources.displayMetrics.density).toInt()
                                            layout.setPadding(padding, padding / 2, padding, padding / 2)
                                            
                                            val picker = com.github.kr328.clash.design.view.ColorPickerView(context)
                                            picker.layoutParams = android.widget.LinearLayout.LayoutParams(
                                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                                (250 * context.resources.displayMetrics.density).toInt()
                                            )
                                            
                                            val input = android.widget.EditText(context)
                                            input.hint = "#RRGGBB"
                                            input.setSingleLine()
                                            val margin = (16 * context.resources.displayMetrics.density).toInt()
                                            val inputParams = android.widget.LinearLayout.LayoutParams(
                                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                            )
                                            inputParams.topMargin = margin
                                            input.layoutParams = inputParams
                                            
                                            layout.addView(picker)
                                            layout.addView(input)
                                            
                                            // Sync Logic
                                            var fromUser = true
                                            picker.setOnColorChangedListener { color ->
                                                if (fromUser) {
                                                    fromUser = false
                                                    val hex = String.format("#%06X", (0xFFFFFF and color))
                                                    input.setText(hex)
                                                    fromUser = true
                                                }
                                            }
                                            
                                            input.addTextChangedListener(object : android.text.TextWatcher {
                                                override fun afterTextChanged(s: android.text.Editable?) {
                                                    if (fromUser) {
                                                        fromUser = false
                                                        try {
                                                            val color = android.graphics.Color.parseColor(s.toString())
                                                            picker.setColor(color)
                                                        } catch (e: Exception) {
                                                            // Ignore
                                                        }
                                                        fromUser = true
                                                    }
                                                }
                                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                            })
                                            
                                            // Set initial random color or red
                                            picker.setColor(android.graphics.Color.RED)
                                            input.setText("#FF0000")

                                            androidx.appcompat.app.AlertDialog.Builder(context)
                                                .setTitle(DesignR.string.enter_color_code)
                                                .setView(layout)
                                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                                    val hex = input.text.toString()
                                                    if (hex.isNotEmpty()) {
                                                        store.heroColorCode = hex
                                                        store.heroBackgroundType = "color"
                                                    }
                                                }
                                                .setNegativeButton(android.R.string.cancel, null)
                                                .show()
                                        } else {
                                            store.heroColorCode = flavors[which].second
                                            store.heroBackgroundType = "color"
                                        }
                                    }
                                    .show()
                            }
                        }
                    }
                }
            }
        }
    }
}
