package io.github.yvescheung.adr.widget.textselection.demo

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import io.github.yvescheung.adr.widget.textselection.KeyboardStatusDetector
import io.github.yvescheung.adr.widget.textselection.TextSelectionController
import io.github.yvescheung.adr.widget.textselection.TextSelectionController.*
import io.github.yvescheung.adr.widget.textselection.demo.databinding.ActivityMainBinding
import io.github.yvescheung.adr.widget.textselection.demo.databinding.LayoutQuickInputBarBinding
import kotlin.math.max

/**
 * @author YvesCheung
 * 2022/6/22
 */
class MainActivity : AppCompatActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //这里用adjustPan+layout高度判断，而不是adjustResize，因为接入方改resize可能会改出其他问题
        val detector = KeyboardStatusDetector.register(this)
        detector.addListener(object : KeyboardStatusDetector.OnChangeListener {

            private var maxHeight = 0

            override fun onVisibleChange(visible: Boolean) {
                binding.quickInputBar.root.visibility = if (visible) VISIBLE else GONE
            }

            override fun onHeightChange(height: Int) {
                val lp = binding.quickInputBar.root.layoutParams as ViewGroup.MarginLayoutParams
                if (lp.bottomMargin != height) {
                    lp.bottomMargin = height
                    binding.quickInputBar.root.layoutParams = lp
                }

                maxHeight = max(maxHeight, height)
                if (maxHeight > 0 && height >= 0) { //随着键盘落下，降低透明度
                    binding.quickInputBar.root.alpha = height.toFloat() / maxHeight
                }
            }
        })
        binding.quickInputBar.root.visibility = if (detector.isVisible) VISIBLE else GONE

        binding.editText.requestFocus()
        val wic = WindowInsetsControllerCompat(window, binding.editText)
        wic.show(WindowInsetsCompat.Type.ime())

        binding.quickInputBar.quickInputM.onClickInputText("m.", binding.editText)
        binding.quickInputBar.quickInputWww.onClickInputText("www.", binding.editText)
        binding.quickInputBar.quickInputCn.onClickInputText(".cn", binding.editText)
        binding.quickInputBar.quickInputCom.onClickInputText(".com", binding.editText)

        val controller =
            TextSelectionController(target = binding.editText, enableWhen = EnableWhen.NotEmpty)
        controller.attachTo(binding.quickInputBar.quickInputSeek)
        controller.addListener(object : OnStatusChangeListener {
            private var expandSeekBar = false
            override fun onTouchEnd(v: View) {
                startInputBarTransition(binding.quickInputBar, false)
                expandSeekBar = false
            }

            override fun onLongPress(type: SelectType) {
                if (!expandSeekBar) startInputBarTransition(binding.quickInputBar, true)
                expandSeekBar = true
            }

            override fun onMove(move: Int, type: SelectType, fromTouch: Boolean) {
                if (!expandSeekBar) startInputBarTransition(binding.quickInputBar, true)
                expandSeekBar = true
            }
        })

        controller.enableMagnifier = binding.cbMagnifier.isChecked
        binding.cbMagnifier.setOnCheckedChangeListener { _, isChecked ->
            controller.enableMagnifier = isChecked
        }

        controller.startActionModeAfterSelection = binding.cbActionMode.isChecked
        binding.cbActionMode.setOnCheckedChangeListener { _, isChecked ->
            controller.startActionModeAfterSelection = isChecked
        }

        val controlMode = { id: Int ->
            when (id) {
                R.id.rb_only_move -> Mode.JustMove
                R.id.rb_only_selection -> Mode.JustSelection
                R.id.rb_short_selection -> Mode.ShortPressSelectionAndLongPressMove
                else -> Mode.ShortPressMoveAndLongPressSelection
            }
        }
        controller.controlMode = controlMode(binding.rgMode.checkedRadioButtonId)
        binding.rgMode.setOnCheckedChangeListener { _, checkedId ->
            controller.controlMode = controlMode(checkedId)
        }
    }

    private fun startInputBarTransition(inputBar: LayoutQuickInputBarBinding, expand: Boolean) {
        val transition = ChangeBounds()
        transition.duration = 200L
        transition.interpolator = DecelerateInterpolator()
        inputBar.quickInputSeek.updateLayoutParams { width = if (expand) MATCH_PARENT else 0 }
        inputBar.quickInputCn.updateLayoutParams { width = inputBar.quickInputCn.measuredWidth }
        inputBar.quickInputM.updateLayoutParams { width = inputBar.quickInputM.measuredWidth }
        inputBar.quickInputWww.updateLayoutParams { width = inputBar.quickInputWww.measuredWidth }
        inputBar.quickInputCom.updateLayoutParams { width = inputBar.quickInputCom.measuredWidth }
        inputBar.quickInputSeek.progressDrawable = ContextCompat.getDrawable(
            this, if (expand) R.drawable.bar_style else R.drawable.bar_style_small
        )
        inputBar.quickInputSeek.thumb = ContextCompat.getDrawable(
            this, if (expand) R.drawable.thumb_style else R.drawable.thumb_style_small
        )
        TransitionManager.beginDelayedTransition(inputBar.root, transition)
    }

    private fun View.onClickInputText(text: String, view: EditText) {
        setOnClickListener {
            if (view.selectionStart == view.selectionEnd) {
                view.text.insert(view.selectionStart, text)
            } else {
                view.text.replace(view.selectionStart, view.selectionEnd, text)
            }
        }
    }
}