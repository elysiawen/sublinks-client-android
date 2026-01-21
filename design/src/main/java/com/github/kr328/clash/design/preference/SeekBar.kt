package com.github.kr328.clash.design.preference

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.SeekBar
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.github.kr328.clash.common.compat.getDrawableCompat
import com.github.kr328.clash.design.databinding.PreferenceSeekbarBinding
import com.github.kr328.clash.design.util.layoutInflater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KMutableProperty0

interface SeekBarPreference : Preference {
    var title: CharSequence
    var icon: Drawable?
    var summary: CharSequence?
    var min: Int
    var max: Int
    var value: Int
    var listener: OnChangedListener?
}

fun PreferenceScreen.seekBar(
    value: KMutableProperty0<Int>,
    @StringRes title: Int,
    @DrawableRes icon: Int? = null,
    @StringRes summary: Int? = null,
    min: Int = 0,
    max: Int = 100,
    configure: SeekBarPreference.() -> Unit = {}
): SeekBarPreference {
    val binding = PreferenceSeekbarBinding
        .inflate(context.layoutInflater, root, false)

    val impl = object : SeekBarPreference {
        override var title: CharSequence
            get() = binding.titleView.text
            set(value) {
                binding.titleView.text = value
            }
        override var icon: Drawable?
            get() = binding.iconView.background
            set(value) {
                binding.iconView.background = value
            }
        override var summary: CharSequence?
            get() = binding.summaryView.text
            set(value) {
                binding.summaryView.text = value
                binding.summaryView.visibility = if (value == null) View.GONE else View.VISIBLE
            }
        override var min: Int = min
        override var max: Int = max
        
        override var value: Int
            get() = binding.seekBar.progress + min
            set(value) {
                binding.seekBar.progress = value - min
                binding.valueView.text = value.toString()
            }
            
        override var listener: OnChangedListener? = null

        override val view: View
            get() = binding.root
    }

    impl.title = context.getText(title)
    if (icon != null) {
        impl.icon = context.getDrawableCompat(icon)
    }
    if (summary != null) {
        impl.summary = context.getText(summary)
    } else {
        impl.summary = null
    }

    binding.seekBar.max = max - min
    
    binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            val currentValue = progress + impl.min
            binding.valueView.text = currentValue.toString()
            if (fromUser) {
                launch(Dispatchers.IO) {
                    value.set(currentValue)
                    withContext(Dispatchers.Main) {
                        impl.listener?.onChanged()
                    }
                }
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })

    impl.configure()

    launch(Dispatchers.Main) {
        val initial = withContext(Dispatchers.IO) {
            value.get()
        }
        impl.value = initial
    }

    addElement(impl)

    return impl
}
