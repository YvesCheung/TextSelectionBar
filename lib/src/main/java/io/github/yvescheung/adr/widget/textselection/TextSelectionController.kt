package io.github.yvescheung.adr.widget.textselection

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.res.Resources
import android.os.*
import android.text.Editable
import android.text.Selection
import android.text.Spannable
import android.text.TextWatcher
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
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.annotation.Size
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.*
import io.github.yvescheung.adr.widget.textselection.TextSelectionController.SelectType
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


/**
 * @author YvesCheung
 * 2022/6/22
 */
@Suppress("MemberVisibilityCanBePrivate")
open class TextSelectionController @JvmOverloads constructor(
    /**
     * 光标所在[EditText]
     */
    val target: TextView,
    /**
     * 长短按触发的行为
     */
    mode: Mode = Mode.ShortPressMoveAndLongPressSelection,
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
    var enableMagnifier: Boolean = true
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
     * 长短按触发的行为
     */
    var controlMode: Mode = mode
        set(value) {
            field = value
            type = resetType()
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

    private val max: Int
        get() = seekBar?.max ?: SEEK_BAR_MAX

    private val min: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) seekBar?.min ?: 0 else 0

    private val progress: Int
        get() = seekBar?.progress ?: 0

    /**
     * 多长时间算长按
     */
    var longPressDuration: Long = 500L

    /**
     * 当进度条移动到端点后，每隔多长时间移动一次光标
     */
    var moveCursorDuration: Long = 100L

    /**
     * 整个进度条宽度可以移动光标多少位置，值越大，光标移动越敏感
     */
    var moveSensitivity = 100f
        set(value) {
            field = if (value <= 0f) 1f else value
        }

    /**
     * 如果false，所有手势都无响应
     */
    var isEnable = true

    /**
     * 当[SelectType.Selection]时，正在移动哪个光标
     */
    private var selectionDirection = SD_UNDEFINE

    /**
     * [attachTo]的进度条
     */
    private var seekBar: SeekBar? = null

    /**
     * 放大镜效果
     */
    private var magnifier: MagnifierHelper? = null

    /**
     * 选中文本的处理
     * @see overrideSelectionAction
     */
    private var selectionImpl: OnSelectionActionCallback = DefaultSelectionActionCallback()

    /**
     * 状态回调
     */
    private val listeners = CopyOnWriteArrayList<OnStatusChangeListener>()

    init {
        if (enableWhen != EnableWhen.None) {
            target.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {
                }

                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, ct: Int) {
                }

                override fun afterTextChanged(text: Editable?) {
                    checkSeekBarEnable(text)
                }
            })
        }
    }

    private val handler = Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            MSG_TOUCH_LONG -> {
                if (controlMode == Mode.ShortPressMoveAndLongPressSelection) {
                    type = SelectType.Selection
                    switchToLongPressMode()
                    listeners.forEach { it.onLongPress(type) }
                } else if (controlMode == Mode.ShortPressSelectionAndLongPressMove) {
                    type = SelectType.Move
                    switchToLongPressMode()
                    listeners.forEach { it.onLongPress(type) }
                }
            }
            MSG_AUTO_CHANGE_PROGRESS -> {
                val currentProgress = seekBar?.progress
                if (currentProgress == min) {
                    moveCursor(-1, type, true)
                } else if (currentProgress == max) {
                    moveCursor(1, type, true)
                }
            }
        }
        true
    }

    private val viewListener = object : View.OnTouchListener, View.OnLayoutChangeListener {
        @Size(2)
        private val location = IntArray(2)
        private val touchSlop = ViewConfiguration.get(target.context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var lastMoveX = 0f
        private var distancePerMove = 0f
        private var x = 0f
        private var y = 0f
        private var touching = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (!isEnable) return true

            v.onTouchEvent(event)

            x = event.rawX
            y = event.rawY
            when (event.actionMasked) {
                ACTION_DOWN -> {
                    touching = true

                    distancePerMove =
                        max(1f, target.resources.displayMetrics.widthPixels / moveSensitivity)
                    downX = x
                    downY = y
                    v.getLocationOnScreen(location)
                    lastMoveX = location[0] + v.width * 0.5f

                    handler.sendEmptyMessageDelayed(MSG_TOUCH_LONG, longPressDuration)

                    listeners.forEach { it.onTouchStart(v) }

                    val move = ((x - lastMoveX) / distancePerMove).roundToInt()
                    if (move != 0 && abs(x - lastMoveX) > touchSlop) {
                        moveCursor(move, type, true)
                        lastMoveX = x
                    }
                }
                ACTION_MOVE -> {
                    if (distance(downX, downY, x, y) > touchSlop * touchSlop) {
                        handler.removeMessages(MSG_TOUCH_LONG)
                    }

                    val move = ((x - lastMoveX) / distancePerMove).roundToInt()
                    if (move != 0) {
                        moveCursor(move, type, true)
                        lastMoveX = x
                    }
                }
                ACTION_UP, ACTION_CANCEL -> {
                    handler.removeMessages(MSG_TOUCH_LONG)
                    handler.removeMessages(MSG_AUTO_CHANGE_PROGRESS)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        magnifier?.dismiss()
                    }

                    if (type == SelectType.Selection && startActionModeAfterSelection) {
                        //accessibility can start ActionMode
                        //曲线救国拉起"剪切/复制/全选"的菜单
                        val start = target.selectionStart
                        val end = target.selectionEnd
                        selectionImpl.removeSelection(target)
                        target.performAccessibilityAction(ACTION_SET_SELECTION,
                            Bundle().apply {
                                putInt(ACTION_ARGUMENT_SELECTION_START_INT, start)
                                putInt(ACTION_ARGUMENT_SELECTION_END_INT, end)
                            }
                        )
                    }
                    onTouchReset()

                    listeners.forEach { it.onTouchEnd(v) }

                    touching = false
                }
            }
            return true
        }

        override fun onLayoutChange(
            v: View,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            oldLeft: Int,
            oldTop: Int,
            oldRight: Int,
            oldBottom: Int
        ) {
            if (touching) {
                v.getLocationOnScreen(location)
                val viewX = location[0]
                val newProgress =
                    (x - viewX) / (right - left - v.paddingLeft - v.paddingRight) * (max - min)
                seekBar?.progress = newProgress.roundToInt()
            }
        }
    }

    open fun moveCursor(move: Int, type: SelectType) {
        moveCursor(move, type, false)
    }

    protected open fun moveCursor(move: Int, type: SelectType, fromTouch: Boolean) {
        if (type == SelectType.Move) {
            selectionImpl.setSelection(
                target = target,
                selection = (target.selectionEnd + move).limit(0, target.text.length)
            )
        } else { //Selection mode
            if (selectionDirection == SD_UNDEFINE) {
                selectionDirection = if (move <= 0) SD_START else SD_END
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
            selectionImpl.setSelection(
                target = target,
                start = start.limit(0, target.text.length),
                end = end.limit(0, target.text.length)
            )
        }

        if (enableMagnifier && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (magnifier == null) {
                magnifier = MagnifierHelper(target)
            }
            magnifier?.show(target.selectionStart)
        }

        if (progress == max || progress == min) {
            handler.sendEmptyMessageDelayed(MSG_AUTO_CHANGE_PROGRESS, moveCursorDuration)
        } else {
            handler.removeMessages(MSG_AUTO_CHANGE_PROGRESS)
        }

        listeners.forEach { it.onMove(move, type, fromTouch) }
    }

    /**
     * @see removeListener
     */
    fun addListener(listener: OnStatusChangeListener) {
        listeners.add(listener)
    }

    /**
     * @see addListener
     */
    fun removeListener(listener: OnStatusChangeListener) {
        listeners.remove(listener)
    }

    /**
     * 自定义实现文本选中的逻辑
     */
    fun overrideSelectionAction(callback: OnSelectionActionCallback?) {
        selectionImpl = callback ?: DefaultSelectionActionCallback()
    }

    @SuppressLint("ClickableViewAccessibility")
    open fun attachTo(seekBar: SeekBar?) {
        this.seekBar = seekBar
        this.seekBar?.setOnTouchListener(viewListener)
        this.seekBar?.addOnLayoutChangeListener(viewListener)
        this.seekBar?.max = SEEK_BAR_MAX
        this.seekBar?.progress = (max - min) / 2
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
        seekBar?.progress = (max - min) / 2
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private class MagnifierHelper(val view: TextView) {

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

    interface OnSelectionActionCallback {
        fun setSelection(target: TextView, selection: Int) {
            setSelection(target, selection, selection)
        }

        fun setSelection(target: TextView, start: Int, end: Int)

        fun removeSelection(target: TextView)
    }

    open class DefaultSelectionActionCallback : OnSelectionActionCallback {
        override fun setSelection(target: TextView, start: Int, end: Int) {
            val text = target.text
            if (text is Spannable) {
                Selection.setSelection(text, start, end)
            }
        }

        override fun removeSelection(target: TextView) {
            val text = target.text
            if (text is Spannable) {
                Selection.removeSelection(text)
            }
        }
    }

    interface OnStatusChangeListener {

        /**
         * 手势按下去时回调
         */
        fun onTouchStart(v: View) {}

        /**
         * 结束手势时回调
         */
        fun onTouchEnd(v: View) {}

        /**
         * 长按替换模式时回调，仅在[Mode.ShortPressMoveAndLongPressSelection]或者
         * [Mode.ShortPressSelectionAndLongPressMove]下会回调。
         */
        fun onLongPress(type: SelectType) {}

        /**
         * 触发光标移动或选择时回调
         * @param move 相对移动位置
         * @param fromTouch 是否由手势滑动触发。直接调用[moveCursor]则为false。
         */
        fun onMove(move: Int, type: SelectType, fromTouch: Boolean) {}
    }

    companion object {

        const val SEEK_BAR_MAX = 10000 //值越大，进度条的滑动越顺畅

        /**
         * 判断是否长按
         */
        private const val MSG_TOUCH_LONG = 1

        /**
         * 当进度条移动到顶端时，继续移动光标
         */
        private const val MSG_AUTO_CHANGE_PROGRESS = 2

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