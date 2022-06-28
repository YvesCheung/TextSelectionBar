package io.github.yvescheung.adr.widget.textselection.demo

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.EditText
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import io.github.yvescheung.adr.widget.textselection.TextSelectionController
import io.github.yvescheung.adr.widget.textselection.TextSelectionController.EnableWhen
import io.github.yvescheung.adr.widget.textselection.TextSelectionController.Mode
import io.github.yvescheung.adr.widget.textselection.demo.databinding.ActivityMainBinding
import io.github.yvescheung.adr.widget.textselection.demo.databinding.LayoutQuickInputBarBinding

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.container.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top < oldBottom - oldTop) { //keyboard show
                binding.quickInputBar.root.visibility = VISIBLE
            } else if (bottom - top > oldBottom - oldTop) {
                binding.quickInputBar.root.visibility = GONE
            }
        }

        binding.editText.requestFocus()
        binding.quickInputBar.quickInputM.onClickInputText("m.", binding.editText)
        binding.quickInputBar.quickInputWww.onClickInputText("www.", binding.editText)
        binding.quickInputBar.quickInputCn.onClickInputText(".cn", binding.editText)
        binding.quickInputBar.quickInputCom.onClickInputText(".com", binding.editText)

        val controller =
            TextSelectionController(target = binding.editText, enableWhen = EnableWhen.NotEmpty)
        controller.attachTo(binding.quickInputBar.quickInputSeek)
        controller.addListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                //Do nothing. Let TextSelectionController to handle.
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                startInputBarTransition(binding.quickInputBar, true)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                startInputBarTransition(binding.quickInputBar, false)
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