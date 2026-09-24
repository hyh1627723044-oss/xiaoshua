# 小刷：独立 Android 关键词遥控器

固定口令本地触发手势，不使用百度 SDK、LLM、通用 ASR、截图、TTS 或 Operit。
应用未声明 INTERNET 权限，模型随 APK 打包。无需 API Key 或另装工具包。

设计参考 bunny-chz/ShortVideoAssistant 的直接手势交互；应用代码独立编写。
SDK 使用 sherpa-onnx 1.13.8，模型使用官方中文 WenetSpeech 3.3M mobile KWS。

## 使用

1. 安装测试 APK，打开“小刷”。最低 Android 8.0。
2. 点击“开启无障碍服务”，在系统设置中启用“小刷手势控制”。
3. 回到应用，点击“开始监听”，授予麦克风权限。建议允许通知，通知上可随时停止。
4. 等待“正在监听”，手动切换到抖音普通竖屏视频页。应用不自动打开聊天窗口。
5. 直接说下一条、上一条、播放、暂停、点赞点赞、查看评论、关闭评论。
6. 说“停止控制”，或点击通知/应用里的停止。锁屏也会停止，解锁不自动恢复。

“小刷＋口令”开关在下次启动监听时生效，例如连续说“小刷下一条”，不需要先唤醒再等提示。
停止控制在两种模式下均为独立口令。

播放与暂停均为单击，不能确保目标播放状态；关闭评论是系统返回，仅应在评论面板打开时说。
点赞是视频中央双击，不包含收藏。评论位置默认为屏幕宽度 92%、高度 65%，可调整高度。
与原项目一样，不读取按钮或视频状态，不支持自动处理弹窗、直播、横屏等特殊页面。
每次动作只读取当前无障碍根节点的包名，不遍历节点树，确认前台为抖音再提交。

## 已知边界

- KWS 识别固定声音模式，不理解否定句；“不要暂停”或视频外放中的“暂停”也可能命中。
- 推荐戴耳机；可选“小刷＋口令”降低碰撞概率，但不保证零误触。
- 动作期间不排队；同口令 900ms 内去重，过期回调不执行，取消手势不自动重试。
- 手势完成只意味着系统完成了触摸注入，不表示页面结果已验证。
- 没有动作后的语音播报、截图验证或窗口切换；状态只显示在应用里。
- 关键词识别准确率、视频外放干扰、耗电、后台录音及真实端到端延迟仍需目标手机测试。
- Android/手机厂商可能要求在应用详情允许“受限制的设置”后才能启用侧载应用的无障碍服务。

## 构建

JDK 17、Android SDK 35、Build Tools 35.0.0、Python 3、curl。
Android Studio 打开此目录即可；SDK 路径写入不提交的 `local.properties`。

```sh
python scripts/prepare_assets.py
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Windows 使用 `gradlew.bat`。脚本下载官方 SDK 和模型，验证 `assets.lock.json` 的 SHA-256，
提取模型并校验口令中的每个音素均在模型词表中。大文件不提交 Git。
APK 输出：`app/build/outputs/apk/debug/app-debug.apk`，使用调试签名，仅用于测试。

官方来源：

- https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8
- https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models
- https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8/android/SherpaOnnxKws
- https://github.com/bunny-chz/ShortVideoAssistant

Sherpa SDK 和模型许可及来源见 `THIRD_PARTY.md` 与 APK assets 中的许可文件。
