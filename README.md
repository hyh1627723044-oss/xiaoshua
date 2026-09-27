# 小刷：独立 Android 语音遥控器

默认模式下，说固定口令，由本地模型识别后直接触发手势，完全离线。不使用百度 SDK、截图、TTS 或 Operit。
可选的云端模式把本地 VAD 切出的短句交给火山引擎 ASR 转写，再用严格口令或 JEV 判断意图，这样就能说自然表达。
云端模式需要你自己的服务凭证，本地关键词模式不发起任何网络请求。

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

## 可选：云端识别

在主界面把“语音识别”切到“字节云 ASR”，首次切换时会弹窗说明音频的去向。然后在“字节 ASR 设置”里填写凭证。

- **火山引擎 ASR**：使用录音文件识别极速版，默认端点为 `https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash`，Resource ID 默认为 `volc.seedasr.auc`（豆包录音文件识别模型 2.0，已实测可用于极速版端点）；如果你开通的是 1.0 极速版，改成 `volc.bigasr.auc_turbo`。填错时会提示“资源未开通（45000030）”。
  - 支持两种凭证：新版控制台的 API Key，或旧版控制台的 AppID + Access Token。
  - “试录并测试”会录 3 秒并上传，用来确认凭证和端点可用。
- **行为判断**：
  - **严格口令**：整句匹配本地别名表，例如“下一个”“点个赞”。“不要点赞”或“点赞然后下一条”不会触发。
  - **JEV 智能判断**：精确口令仍然在本地处理；其他文本交给 JEV，从 9 个固定行为（含“不执行”）中选一个。置信度低于 0.80 不执行，点赞要求 0.90；第一名和第二名差距过小也不执行。默认模型固定为 `jev-1.13.0`。
- **停止控制**：云端模式下同时运行一个只识别“停止控制”的本地模型，断网时也能用语音停止。
- **服务地址**：两个服务 URL 都可以改成兼容的中转站，必须是 https 完整端点。
- **隐私**：
  - 只上传 VAD 判定为一句话的短音频，最长 8 秒，静音时不上传。
  - JEV 只接收最终转写文字。
  - 音频和转写不写文件、不写日志。
  - 同一时间只处理一句话，处理期间说的新句子直接丢弃。
  - 云端结果在语句结束 5 秒后过期。
  - 网络、识别、判断出错时一律不执行动作。
- **密钥保存**：用 Android Keystore 加密保存。Root 或已被攻破的设备上不能保证安全。JEV 官方建议密钥只放在服务端，这里保存的是你自己设备上的密钥。
- **高级设置**：可以调整 VAD 起始阈值（结束阈值自动取起始值减 0.15）、起始/结束确认时长、前置缓存，以及 JEV 的两个阈值。

## 已知边界

- KWS 识别固定声音模式，不理解否定句；“不要暂停”或视频外放中的“暂停”也可能命中。
- 推荐戴耳机；可选“小刷＋口令”降低碰撞概率，但不保证零误触。
- 动作期间不排队；同口令 900ms 内去重，过期回调不执行，取消手势不自动重试。
- 手势完成只意味着系统完成了触摸注入，不表示页面结果已验证。
- 没有动作后的语音播报、截图验证或窗口切换；状态只显示在应用里。
- 关键词识别准确率、视频外放干扰、耗电、后台录音及真实端到端延迟仍需目标手机测试。
- Android/手机厂商可能要求在应用详情允许“受限制的设置”后才能启用侧载应用的无障碍服务。
- 云端模式：VAD 无法区分你的声音和视频里的人声，外放时可能上传视频对白并误判。
- 云端模式：已于 2026-09-28 实测极速版端点接受 base64 `audio.data`，配合 `volc.seedasr.auc` 正常返回。

## 构建

JDK 17、Android SDK 35、Build Tools 35.0.0、Python 3、curl。
Android Studio 打开此目录即可；SDK 路径写入不提交的 `local.properties`。

```sh
python scripts/prepare_assets.py
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Windows 使用 `gradlew.bat`。脚本下载官方 SDK 和模型，验证 `assets.lock.json` 的 SHA-256，
提取模型并校验口令中的每个音素均在模型词表中。大文件不提交 Git；
约 629 KB 的 Silero VAD 模型 `app/src/main/assets/vad/silero_vad.onnx` 直接提交，脚本只校验它的 SHA-256。
APK 输出：`app/build/outputs/apk/debug/app-debug.apk`，使用调试签名，仅用于测试。

官方来源：

- https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8
- https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models
- https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8/android/SherpaOnnxKws
- https://github.com/bunny-chz/ShortVideoAssistant
- https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models （silero_vad.onnx）
- https://www.volcengine.com/docs/6561/1631584 （录音文件识别极速版）
- https://jevtypesafeai.com/docs （JEV Decision API）

Sherpa SDK 和模型许可及来源见 `THIRD_PARTY.md` 与 APK assets 中的许可文件。
