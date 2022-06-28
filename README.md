# TextSelectionBar

:mag:长文本输入栏神器！
:straight_ruler:通过拖动进度条移动光标或选中文本。

## 预览

### 短按拖动：移动光标

![](https://raw.githubusercontent.com/YvesCheung/TextSelectionBar/master/art/shortPressMoveCursor.gif)

### 长按拖动：选中文本

![](https://raw.githubusercontent.com/YvesCheung/TextSelectionBar/master/art/longPressSelection.gif)

## 特性
1. 支持 **短按移动长按选中**，**短按选中长按移动**，**仅移动光标**，**仅选中文本** 四种模式。
2. 可选开启**放大镜**。
    ![](https://raw.githubusercontent.com/YvesCheung/TextSelectionBar/master/art/option_magnifier.jpg)

3. 可选开启“剪切/复制/粘贴/全选”菜单。
    ![](https://raw.githubusercontent.com/YvesCheung/TextSelectionBar/master/art/option_text_action_mode.jpg)
    
## 使用

### 一行完成接入

Ui使用系统原生的 `EditText` 和 `SeekBar`，样式由使用方自行实现。
本库只负责控制逻辑。

```kotlin
TextSelectionController(editText).attachTo(seekBar)
```

### 更多可选配置

```kotlin
seekBar.max = 100 //进度条本身属性，max值越大，拖动进度条时光标移动越快
val controller = TextSelectionController(
    target = editText, 
    mode = Mode.ShortPressMoveAndLongPressSelection, //短按移动，长按选中
    enableWhen = EnableWhen.NotEmpty, //只有EditText非空才可用
    startActionModeAfterSelection = true, //选中后是否显示“剪切/复制”菜单
    enableMagnifier = true //是否开启放大镜，Android9+支持
)
controller.longPressDuration = 500L //手势多长时间算长按
controller.moveCursorDuration = 100L //当进度条拖到尽头后，每隔多长时间移动一次光标
controller.attachTo(seekBar)

```
