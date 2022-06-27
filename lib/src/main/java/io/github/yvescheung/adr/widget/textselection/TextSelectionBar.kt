package io.github.yvescheung.adr.widget.textselection

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.SeekBar

/**
 * @author YvesCheung
 * 2022/6/22
 */
class TextSelectionBar @JvmOverloads constructor(
    context: Context,
    attr: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attr, defStyleAttr) {

    private val seekBar: SeekBar = SeekBar(context, attr)

    init {
        seekBar.max = 100
        seekBar.progress = 50
        addView(seekBar)
    }

    fun setController(controller: TextSelectionController) {
        controller.attachTo(seekBar)
    }
}