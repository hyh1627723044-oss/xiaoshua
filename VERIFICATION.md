# Verification: 0.2.0（2026-09-27）

本地构建环境：Windows，Microsoft OpenJDK 17，Gradle 8.11.1，Android SDK 35，Build Tools 35.0.0。

## 已完成

- `:app:assembleDebug`：构建成功，产物为 `dist/short-video-kws-0.2.0-debug.apk`，同目录下附 SHA-256 文件，使用调试签名。
- APK 内包含以下资源：
  - `vad/silero_vad.onnx`：643,854 字节，SHA-256 已锁定在 `assets.lock.json`。
  - `keywords-stop.txt`。
  - 原有的两份口令表和 KWS 模型。
- `:app:testDebugUnitTest`：阶段 1–3 提交时共 39 个测试，全部通过。
  - 覆盖请求过期与去重、整句口令匹配（否定句和组合句不命中）、VAD 状态机（起止确认、迟滞、前置缓存、150ms 最短、8s 强制结束、NaN）、WAV 头。
  - 用 MockWebServer 覆盖火山 ASR 的两种鉴权头、自定义 URL、状态码映射、超时、取消和错误文本不含密钥。
  - 同样用 MockWebServer 覆盖 JEV 的请求结构、固定标签、阈值、并列、畸形响应和 HTTP 错误。
  - 覆盖 AES-GCM 密文的往返、nonce 不重复、篡改、截断和 AAD 字段绑定。
- 源码中没有真实凭证：检查了 `jv_live_` 前缀和硬编码的 `X-Api-Key` 值。

## 本轮未执行

按用户要求，阶段 4–6（音频管线、界面、资源）只做了编译，没有重新运行单元测试和 `:app:lintDebug`。下次发布前应补跑：

```sh
python scripts/prepare_assets.py
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## 需要真机验收

没有连接 Android 设备，以下各项都未验证：

- **本地模式**：断网时的回归测试，功能应与 0.1.0 一致。
- **Android Keystore**：保存、覆盖、清除凭证，以及清除应用数据后的行为。
- **字节 ASR**：
  - “试录并测试”，用于确认极速版接受 `audio.data` base64 正文（最新文档没有列出该字段）。
  - 新版 API Key 和旧版 AppID + Access Token 两种鉴权。
- **JEV**：“测试连接”。
- **自然表达**：端到端测试，例如“换一个”“帮我点个赞”“不要点赞”。
- **停止**：云端模式下断网时语音“停止控制”仍然生效；锁屏停止、通知停止、监听中改设置被锁定。
- **VAD**：戴耳机和外放两种情况下的分段效果与误触率，以及高级设置调整后的效果。
- **性能**：严格口令链路和 JEV 链路的端到端延迟、耗电，以及红米后台保活。
