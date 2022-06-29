package io.github.yvescheung.adr.widget.textselection

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import androidx.annotation.RequiresApi
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("MemberVisibilityCanBePrivate")
interface KeyboardStatusDetector {

    interface OnChangeListener {

        fun onHeightChange(height: Int) {}

        fun onVisibleChange(visible: Boolean) {}
    }

    fun addListener(listener: OnChangeListener)

    fun removeListener(listener: OnChangeListener)

    val isVisible: Boolean

    companion object {

        private const val keyTag = (3 shl 24) or 888888

        fun register(activity: Activity): KeyboardStatusDetector {
            return register(activity.window)
        }

        fun register(window: Window): KeyboardStatusDetector {
            val decorView = window.decorView
            val alreadyRegister =
                decorView.getTag(keyTag) as? KeyboardStatusDetector
            return if (alreadyRegister != null) {
                alreadyRegister
            } else {
                val detector: Impl =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ImplR()
                    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ImplM()
                    else Impl()
                detector.register(decorView)
                decorView.setTag(keyTag, detector)
                detector
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private open class ImplR : ImplM() {

        override fun register(decorView: View) {
            val callback =
                object : WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    override fun onProgress(
                        insets: WindowInsets,
                        animations: MutableList<WindowInsetsAnimation>
                    ): WindowInsets {
                        val navigation = insets.getInsets(WindowInsets.Type.navigationBars())
                        val ime = insets.getInsets(WindowInsets.Type.ime())
                        Log.i(
                            "Yves", "onProgress ime = $ime navigation = $navigation" +
                                    ", visible = ${insets.isVisible(WindowInsets.Type.ime())}"
                        )
                        listeners.forEach { it.onHeightChange(ime.bottom - navigation.bottom) }

                        val newVisible = insets.isVisible(WindowInsets.Type.ime())
                        if (isVisible != newVisible) {
                            listeners.forEach { it.onVisibleChange(newVisible) }
                            isVisible = newVisible
                        }

                        return insets
                    }
                }
            decorView.setWindowInsetsAnimationCallback(callback)

            isVisible = decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) ?: false
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private open class ImplM : Impl() {

        override fun register(decorView: View) {
            decorView.viewTreeObserver.addOnGlobalLayoutListener {
                val windowInset =
                    WindowInsetsCompat.toWindowInsetsCompat(decorView.rootWindowInsets)
                val ime = windowInset.getInsets(WindowInsetsCompat.Type.ime())
                val navigation = windowInset.getInsets(WindowInsetsCompat.Type.navigationBars())

                val newVisible = windowInset.isVisible(WindowInsetsCompat.Type.ime())
                if (isVisible != newVisible) {
                    listeners.forEach { it.onVisibleChange(newVisible) }
                    listeners.forEach { it.onHeightChange(ime.bottom - navigation.bottom) }
                    isVisible = newVisible
                }
                Log.i("Yves", "ime = $ime navigation = $navigation")
            }

            isVisible = decorView.rootWindowInsets?.let {
                WindowInsetsCompat
                    .toWindowInsetsCompat(it)
                    .isVisible(WindowInsetsCompat.Type.ime())
            } ?: false
        }
    }

    private open class Impl : KeyboardStatusDetector {

        override var isVisible = false

        protected val listeners = CopyOnWriteArrayList<OnChangeListener>()

        override fun addListener(listener: OnChangeListener) {
            listeners.add(listener)
        }

        override fun removeListener(listener: OnChangeListener) {
            listeners.remove(listener)
        }

        open fun register(decorView: View) {
            var visibleHeight = 0
            var windowHeight = 0
            decorView.viewTreeObserver.addOnGlobalLayoutListener {
                val height = decorView.windowVisibleHeight()
                if (windowHeight == 0) {
                    visibleHeight = height
                    windowHeight = height
                } else if (visibleHeight != height) {
                    if (height - visibleHeight > SOFT_KEY_BOARD_MIN_HEIGHT) {
                        if (isVisible) {
                            isVisible = false
                            listeners.forEach { it.onVisibleChange(isVisible) }
                        }
                    } else if (visibleHeight - height > SOFT_KEY_BOARD_MIN_HEIGHT) {
                        if (!isVisible) {
                            isVisible = true
                            listeners.forEach { it.onVisibleChange(isVisible) }
                        }
                    }

                    if (isVisible) {
                        listeners.forEach { it.onHeightChange(windowHeight - height) }
                    }
                    visibleHeight = height
                }
            }
        }

        private fun View.windowVisibleHeight(): Int {
            return Rect().let {
                this.getWindowVisibleDisplayFrame(it)
                it.height()
            }
        }

        companion object {
            private const val SOFT_KEY_BOARD_MIN_HEIGHT = 300
        }
    }
}