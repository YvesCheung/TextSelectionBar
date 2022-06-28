# TextSelectionBar

:mag: 长文本输入栏神器！

:straight_ruler: 通过拖动进度条移动光标或选中文本。

![Build](https://github.com/YvesCheung/TextSelectionBar/workflows/Build/badge.svg) [![](https://jitpack.io/v/YvesCheung/TextSelectionBar.svg)](https://jitpack.io/#YvesCheung/TextSelectionBar)

## Preview

|短按拖动：移动光标|长按拖动：选中文本|
|:---:|:---:|
|![](https://raw.githubusercontent.com/YvesCheung/TextSelectionBar/master/art/shortPressMoveCursor.gif)|![](https://raw.githubusercontent.com/YvesCheung/TextSelectionBar/master/art/longPressSelection.gif)|

## Feature

1. 支持 **短按移动长按选中**，**短按选中长按移动**，**仅移动光标**，**仅选中文本** 四种模式。
2. 可选开启**放大镜**。
    
    <img src="https://raw.githubusercontent.com/YvesCheung/TextSelectionBar/master/art/option_magnifier.jpg" width="400"/>

3. 可选开启“剪切/复制/粘贴/全选”菜单。
    
    <img src="https://raw.githubusercontent.com/YvesCheung/TextSelectionBar/master/art/option_text_action_mode.jpg" width="400"/>
    
## Usage

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

## Install

```groovy
allprojects {
    repositories {
        //...
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.YvesCheung:TextSelectionBar:x.y.z'
}
```

x.y.z replace with [![](https://jitpack.io/v/YvesCheung/TextSelectionBar.svg)](https://jitpack.io/#YvesCheung/TextSelectionBar)

## License

	Copyright 2022 Yves Cheung
	
   	Licensed under the Apache License, Version 2.0 (the "License");
   	you may not use this file except in compliance with the License.
   	You may obtain a copy of the License at

       	http://www.apache.org/licenses/LICENSE-2.0

   	Unless required by applicable law or agreed to in writing, software
   	distributed under the License is distributed on an "AS IS" BASIS,
   	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   	See the License for the specific language governing permissions and
   	limitations under the License.
