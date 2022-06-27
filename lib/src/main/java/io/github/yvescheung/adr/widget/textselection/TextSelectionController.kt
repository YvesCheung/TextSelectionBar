package io.github.yvescheung.adr.widget.textselection

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.res.Resources
import android.os.*
import android.text.Selection
import android.util.TypedValue
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.Magnifier
import android.widget.SeekBar
import androidx.annotation.RequiresApi
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.*
import androidx.core.widget.doAfterTextChanged
import io.github.yvescheung.adr.widget.textselection.TextSelectionController.SelectType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


/**
 * @author YvesCheung
 * 2022/6/22
 */
@Suppress("MemberVisibilityCanBePrivate")
open class TextSelectionController(
    /**
     * 光标所在[EditText]
     */
    val target: EditText,
    /**
     * 长短按触发的行为
     */
    var controlMode: Mode = Mode.ShortPressMoveAndLongPressSelection,
    /**
     * 当[target]满足一定条件时，该控件才可用
     */
    var enableWhen: EnableWhen = EnableWhen.None,
    /**
     * 通过[SelectType.Selection]选定范围后，是否拉起"剪切/复制/全选"的菜单
     */
    var startActionModeAfterSelection: Boolean = true,
    /**
     * 在Android9上是否打开[Magnifier]放大光标位置
     */
    var enableMagnifier: Boolean = true,
    /**
     * 多长时间算长按
     */
    var longPressDuration: Long = 500L
) {
    enum class SelectType {
        /**
         * 移动光标
         * move the cursor
         */
        Move,

        /**
         * 移动选择范围
         * modify the range of selection
         */
        Selection;
    }

    /**
     * 手势触摸后，光标的行为
     */
    enum class Mode {
        /**
         * 默认移动光标，长按控件后改为选择范围
         */
        ShortPressMoveAndLongPressSelection,

        /**
         * 默认选择范围，长按控件后改为移动光标
         */
        ShortPressSelectionAndLongPressMove,

        /**
         * 无论是否长按都是移动光标
         */
        JustMove,

        /**
         * 无论是否长按都是选择范围
         */
        JustSelection
    }

    /**
     * 是否当[target]满足一定条件时，控件才可用
     */
    enum class EnableWhen {
        /**
         * [EditText.getText]不为空
         */
        NotEmpty,

        /**
         * 该库不处理
         */
        None;
    }

    /**
     * 当前移动[seekBar]如何调整[target]的光标
     * @see SelectType
     */
    private var type = resetType()

    private fun resetType(): SelectType {
        return if (controlMode == Mode.ShortPressMoveAndLongPressSelection ||
            controlMode == Mode.JustMove
        ) {
            SelectType.Move
        } else {
            SelectType.Selection
        }
    }

    private val max
        get() = seekBar?.max ?: 100

    private val min
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) seekBar?.min ?: 0 else 0

    private var progressOnPress = (max - min) / 2
    private var currentProgress = progressOnPress

    private var selectionStartOnPress = 0
    private var selectionEndOnPress = 0

    /**
     * 当[SelectType.Selection]时，正在移动哪个光标
     */
    private var selectionDirection = SD_UNDEFINE

    private var seekBar: SeekBar? = null

    private var magnifier: MagnifierHelper? = null

    init {
        if (enableWhen != EnableWhen.None) {
            target.doAfterTextChanged { text ->
                checkSeekBarEnable(text)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper()) {
        if (it.what == TOUCH_LONG) {
            if (controlMode == Mode.ShortPressMoveAndLongPressSelection) {
                type = SelectType.Selection
                switchToLongPressMode()
            } else if (controlMode == Mode.ShortPressSelectionAndLongPressMove) {
                type = SelectType.Move
                switchToLongPressMode()
            }
        }
        true
    }

    private val onSeekbarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) changeProgress(progress, type)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            //Do nothing
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            //Do nothing
        }
    }

    private val onTouchListener = object : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(target.context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    handler.sendEmptyMessageDelayed(TOUCH_LONG, longPressDuration)
                    selectionStartOnPress = target.selectionStart
                    selectionEndOnPress = target.selectionEnd

                    seekBar?.let {
                        progressOnPress = it.progress
                        currentProgress = progressOnPress
                    }
                }
                ACTION_MOVE -> {
                    if (distance(downX, downY, event.x, event.y) > touchSlop * touchSlop) {
                        handler.removeMessages(TOUCH_LONG)
                    }
                }
                ACTION_UP, ACTION_CANCEL -> {
                    handler.removeMessages(TOUCH_LONG)
                }
            }

            v?.onTouchEvent(event)

            if (event.actionMasked == ACTION_UP ||
                event.actionMasked == ACTION_CANCEL
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    magnifier?.dismiss()
                }

                if (type == SelectType.Selection && startActionModeAfterSelection) {
                    //accessibility can start ActionMode
                    //曲线救国拉起"剪切/复制/全选"的菜单
                    val start = target.selectionStart
                    val end = target.selectionEnd
                    Selection.removeSelection(target.text)
                    target.performAccessibilityAction(ACTION_SET_SELECTION,
                        Bundle().apply {
                            putInt(ACTION_ARGUMENT_SELECTION_START_INT, start)
                            putInt(ACTION_ARGUMENT_SELECTION_END_INT, end)
                        }
                    )
                }
                onTouchReset()
            }
            return true
        }
    }

    open fun changeProgress(newProgress: Int, type: SelectType) {
        val move =
            if (newProgress == currentProgress && newProgress == min) -1
            else if (newProgress == currentProgress && newProgress == max) 1
            else newProgress - currentProgress
        if (type == SelectType.Move) {
            target.setSelection((target.selectionStart + move).limit(0, target.text.length))
        } else { //Selection mode
            if (selectionDirection == SD_UNDEFINE) {
                selectionDirection = if (newProgress < progressOnPress) SD_START else SD_END
            } else if (target.selectionStart + move > target.selectionEnd) {
                selectionDirection = SD_END
            } else if (target.selectionEnd + move < target.selectionStart) {
                selectionDirection = SD_START
            }
            val start =
                if (selectionDirection == SD_END) target.selectionStart
                else target.selectionStart + move
            val end =
                if (selectionDirection == SD_START) target.selectionEnd
                else target.selectionEnd + move
            target.setSelection(
                start.limit(0, target.text.length),
                end.limit(0, target.text.length)
            )
        }
        currentProgress = newProgress

        if (enableMagnifier && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (magnifier == null) {
                magnifier = MagnifierHelper(target)
            }
            magnifier?.show(target.selectionStart)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    open fun attachTo(seekBar: SeekBar?) {
        if (seekBar != null) {
            seekBar.setOnTouchListener(onTouchListener)
            seekBar.setOnSeekBarChangeListener(onSeekbarChangeListener)
        }
        this.seekBar = seekBar
        checkSeekBarEnable(target.text)
    }

    private fun checkSeekBarEnable(text: CharSequence?) {
        if (enableWhen == EnableWhen.NotEmpty) {
            seekBar?.isEnabled = !text.isNullOrEmpty()
        }
    }

    protected open fun switchToLongPressMode() {
        val vb = target.context.getSystemService(Service.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vb?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else {
            vb?.vibrate(600L)
        }
    }

    protected open fun onTouchReset() {
        selectionDirection = SD_UNDEFINE
        type = resetType()
        seekBar?.progress = progressOnPress
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private class MagnifierHelper(val view: EditText) {

        private var magnifier: Magnifier? = null

        private var isShowing = false

        private var startX: Float = 0f
        private var startY: Float = 0f
        private var currentX: Float = 0f
        private var currentY: Float = 0f
        private var endX: Float = 0f
        private var endY: Float = 0f

        private val animator: ValueAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 100L //android.widget.Editor.MagnifierMotionAnimator.DURATION
                interpolator = LinearInterpolator()
                addUpdateListener { animation: ValueAnimator ->
                    currentX = (startX
                            + (endX - startX) * animation.animatedFraction)
                    currentY = (startY
                            + (endY - startY) * animation.animatedFraction)
                    magnifier?.show(currentX, currentY)
                }
            }

        private val onTextViewDraw = ViewTreeObserver.OnDrawListener {
            view.post {
                if (isShowing) {
                    magnifier?.update()
                }
            }
        }

        fun show(cursor: Int) {
            val lineNumber = view.layout.getLineForOffset(cursor)
            val x = (view.layout.getPrimaryHorizontal(cursor) - view.scrollX) * view.scaleX
            val y = ((view.layout.getLineTop(lineNumber)
                    + view.layout.getLineTop(lineNumber + 1)) / 2.0f
                    + view.totalPaddingTop - view.scrollY) * view.scaleY

            if (magnifier == null) {
                val zoom = 1.5f
                val aspectRatio = 4f
                val minHeight = 20f.dp
                magnifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val sourceHeight =
                        view.layout.getLineTop(lineNumber + 1) - view.layout.getLineTop(lineNumber)
                    val height = (sourceHeight * zoom + 8f.dp /*padding*/).toInt()
                    val width = (aspectRatio * max(sourceHeight, minHeight)).toInt()
                    Magnifier.Builder(view).setSize(width, height)
                        .setCornerRadius(height * 0.5f)
                        .setInitialZoom(zoom).build()
                } else {
                    Magnifier(view)
                }
            }

            if (isShowing && endX != x && endY != y) {
                if (animator.isRunning) {
                    animator.cancel()
                    startX = currentX
                    startY = currentY
                } else {
                    startX = endX
                    startY = endY
                }
                animator.start()
            } else {
                if (!animator.isRunning) {
                    magnifier?.show(x, y)
                }
            }
            endX = x
            endY = y

            if (!isShowing) {
                view.viewTreeObserver.addOnDrawListener(onTextViewDraw)
            }
            isShowing = true
        }

        fun dismiss() {
            animator.cancel()
            view.viewTreeObserver.removeOnDrawListener(onTextViewDraw)
            magnifier?.dismiss()
            magnifier = null
            isShowing = false
        }
    }

    companion object {
        private const val TOUCH_LONG = 1

        private const val SD_UNDEFINE = 0
        private const val SD_START = 1
        private const val SD_END = 2

        private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            return (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)
        }

        private fun Int.limit(minValue: Int, maxValue: Int): Int {
            return max(minValue, min(this, maxValue))
        }

        private val Float.dp: Int
            get() = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                this,
                Resources.getSystem().displayMetrics
            ).roundToInt()
    }
}