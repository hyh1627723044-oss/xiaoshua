# 小刷可选云端 ASR 与 JEV 行为判断设计

日期：2026-09-26  
状态：已确认并实现（0.2.0）。2026-09-26 已按官方文档修正 5.4、5.6、9.2 和 9.3 节，并补充了云端模式下的离线停止口令（5.7）。

## 1. 背景

“小刷”当前是独立 Android 应用。它持续读取麦克风的 16 kHz、单声道、16-bit PCM，使用本地 sherpa-onnx 关键词模型直接产生 `NEXT`、`LIKE` 等行为标签，再通过无障碍手势操作抖音。当前应用没有网络权限、后端、账号或 API Key，音频不上传也不落盘。

本次设计增加两项可选能力：

1. 使用字节火山引擎的云端 ASR，提高自然语音转写准确率。
2. 使用 JEV 把转写文本判断为固定行为，使用户可以说自然表达，而不局限于固定口令。

两项能力都集成在“小刷”应用内，不依赖 Operit，也不建设自有后端。用户填写自己的服务凭证，并可配置服务 URL，以便使用兼容中转站。

## 2. 目标

- 保留当前完全离线、低延迟的本地关键词模式，并作为默认模式。
- 增加“本地 VAD + 字节短音频非流式 ASR”模式。
- 云端 ASR 只上传本地 VAD 切出的短语音，不上传整个监听时段。
- 云端 ASR 转写后，可选严格口令判断或 JEV 智能判断。
- JEV 只能从应用定义的固定行为集合中选择，不能生成或执行任意工具调用。
- 字节 ASR 与 JEV 分别支持自定义服务 URL、凭证测试和凭证清除。
- 凭证不写入 APK、仓库、日志或明文配置，使用 Android Keystore 加密保存。
- 保留现有的前台抖音检查、竖屏检查、动作去重、过期丢弃、忙碌拒绝和锁屏停止。
- 网络、ASR、JEV 或解析失败时不执行动作。

## 3. 非目标

- 第一版不建设或部署自有中转后端。
- 第一版不支持持续流式 ASR、边说边出字或长语音听写。
- 第一版不支持除字节以外的云端 ASR 提供商。
- 第一版不让 JEV 增加、组合或直接调用新的 Android 操作。
- 第一版不保存录音文件、完整转写历史或云端请求正文。
- 第一版不解决手机扬声器中的人声与用户本人声音的可靠区分问题。

## 4. 已确定的产品行为

### 4.1 语音识别模式

应用提供两种识别模式：

| 模式 | 音频处理 | 网络 | 输出 |
| --- | --- | --- | --- |
| 本地关键词 | 持续送入本地 sherpa-onnx KWS | 无 | 固定 `Command` 标签 |
| 字节云 ASR | Silero VAD 在本地切出一句话，再一次性上传 | 有 | 最终转写文本 |

现有用户升级后继续使用“本地关键词”，不会自动启用网络。

### 4.2 行为判断模式

当语音识别选择“字节云 ASR”时，提供两种行为判断方式：

| 模式 | 行为 |
| --- | --- |
| 严格口令 | 对规范化后的最终文本执行本地别名表匹配 |
| JEV 智能判断 | 精确命令走本地快速路径，其余文本交给 JEV 的 `choice` 决策 |

选择“本地关键词”时，行为判断固定为本地标签映射，JEV 选项禁用。

### 4.3 固定行为集合

JEV 只能返回以下值：

```text
NEXT
PREVIOUS
PLAY
PAUSE
LIKE
COMMENTS
CLOSE_COMMENTS
STOP
NO_ACTION
```

`NO_ACTION` 是必选项。无法确认用户意图、否定句、闲聊、视频对白或低置信度结果都必须落入 `NO_ACTION`。

`STOP` 的明确口令始终优先由本地规则处理，停止功能不依赖 JEV 或网络。

## 5. 总体架构

```text
                         ┌─ LocalKeywordRecognizer ───────────────┐
AudioCapture ─ PCM ──────┤                                       │
                         └─ SileroUtteranceSegmenter              │
                                  │ 完整短语音                    │
                                  ▼                               │
                            ByteAsrClient                         │
                                  │ 最终文本                      │
                                  ▼                               │
                    ExactIntentResolver / JevIntentResolver       │
                                  │ CommandDecision               │
                                  └───────────────┬───────────────┘
                                                  ▼
                                            CommandGate
                                                  ▼
                                           GestureService
```

### 5.1 AudioCapture

- 全应用只创建一个 `AudioRecord`，避免多个引擎争抢麦克风。
- 固定使用 16 kHz、单声道、16-bit PCM。
- 保留当前 `MediaRecorder.AudioSource.VOICE_RECOGNITION`，并在真机验证回声消除和降噪表现。
- 每次开始监听创建采集会话；停止、锁屏或服务销毁时立即释放。
- 根据当前模式把 PCM 交给本地关键词识别器或 Silero 分段器。

### 5.2 LocalKeywordRecognizer

- 封装现有 `KeywordEngine` 行为。
- 继续使用 `keywords.txt` 或 `keywords-prefixed.txt`。
- 不触发网络请求，不依赖云端配置。
- 维持当前的低延迟和 700ms 本地命令新鲜度限制。

### 5.3 SileroUtteranceSegmenter

- 通过现有 `sherpa-onnx-1.13.8.aar` 中的 `Vad`、`VadModelConfig` 和 `SileroVadModelConfig` 调用 Silero VAD。
- 使用 sherpa-onnx 官方 `silero_vad.onnx`，模型约 629 KB，随 APK 打包并锁定 SHA-256。
- 推理完全在手机本地完成，不向第三方发送 VAD 概率或静音片段。
- 维护前置环形缓冲、开始候选、说话中和结束候选四种状态。

### 5.4 ByteAsrClient

- 接收完整的短 PCM 语句，在内存中封装为 16 kHz、单声道、16-bit WAV。
- 使用字节火山引擎短音频非流式 HTTP 接口，一次请求提交完整语句；第一版只支持能够直接接收音频正文的端点，不支持要求先把音频上传到公网 URL 的接口。
- 默认服务端点为录音文件识别极速版 `https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash`，一次请求就返回结果。
  - 原先写的 `/api/v3/auc/bigmodel` 是标准版的“提交 + 轮询”接口，只接受 `audio.url`，所以不采用。
- 鉴权支持两种方式：
  - 新版控制台：`X-Api-Key`。
  - 旧版控制台：`X-Api-App-Key` 加 `X-Api-Access-Key`。
- 另外发送 `X-Api-Resource-Id`（默认 `volc.seedasr.auc`，即模型 2.0；2026-09-28 实测在极速版端点上可用且接受 `audio.data`。1.0 极速版为 `volc.bigasr.auc_turbo`）、`X-Api-Request-Id`（UUID）和 `X-Api-Sequence: -1`。
- 请求体：`{user:{uid}, audio:{data:<WAV 的 base64>, format:"wav"}, request:{model_name:"bigmodel", enable_punc, enable_itn}}`。
- 状态从响应头 `X-Api-Status-Code` 读取：
  - `20000000` 表示成功，文本在 `result.text`。
  - `20000003`（静音）和 `45000002`（空音频）按“没说话”处理。
  - 其他状态码显示简短的错误原因。
- 最新版文档没有列出 `audio.data`，旧版文档写明支持，需要用“试录并测试”在真机上确认。
- 只接受最终转写文本，不处理部分结果。
- 服务 URL 是完整端点 URL，可以替换为兼容中转站地址。
- 第一版使用 OkHttp 完成 HTTPS、请求封装、超时和取消；字节官方协议由独立适配器封装，中转站需要兼容该请求和响应格式；JSON 使用 Android `org.json`。

### 5.5 ExactIntentResolver

- 对文本执行 Unicode 空白、常见标点和全半角规范化。
- 只匹配明确配置的完整短句和别名，不做包含匹配。
- 否定词或额外语义存在时不执行，例如“不要点赞”不能因为包含“点赞”而命中。
- 在 JEV 模式中，明确且无歧义的固定口令仍走本地快速路径，以减少延迟和费用。

### 5.6 JevIntentResolver

- 默认端点为 `https://jevtypesafeai.com/api/v1/decide`。
- 服务 URL 是完整端点 URL，可以替换为兼容中转站地址。
- 默认模型固定为 `jev-1.13.0`，可以在 JEV 设置中修改。
  - JEV 官方建议在生产环境固定版本，因为 `jev-latest` 升级后置信度分布可能变化，使阈值失效。
- 鉴权头为 `Authorization: Bearer <key>`。
- 请求体：`{model, state:<转写文本>, questions:{intent:{type:"choice", instructions, criteria:{行为:描述}}}}`。
  - `criteria` 只包含固定行为和 `NO_ACTION`。
  - 请求中不包含音频、屏幕节点或设备标识。
- 结果在 `answers.intent` 中，包含 `choice`、`confidence` 和 `probabilities`。
- 第一名与第二名的概率差不足 `0.10` 时视为并列。
- `choice` 必须是概率最高的那一项，否则视为结果不一致。
- 全局默认接受阈值为 `0.80`；`LIKE` 的接受阈值为 `0.90`。
- 返回未知行为、字段缺失、非有限数值、低置信度或并列不明确时统一返回 `NO_ACTION`。

### 5.7 CommandGate 与 GestureService

- 将识别结果统一封装为 `CommandRequest`，至少包含 `command`、`source`、`utteranceId`、`capturedAtElapsedMs` 和 `expiresAtElapsedMs`。
- 本地 KWS 请求沿用短新鲜度窗口。
- 云端请求从语句结束开始计算，默认在 5 秒后过期。
- 停止监听、模式切换、锁屏、服务重建或出现更新的会话代次后，旧回调全部丢弃。
- 云端识别采用 single-flight：同一时间最多处理一个完整语句，不排队执行过期命令。
- 动作提交前仍由 `GestureService` 检查抖音包名、屏幕状态、竖屏状态和忙碌状态。
- 云端模式下，同一路 PCM 还会送入一个只含“停止控制”的本地 KWS（`keywords-stop.txt`）。这样语音停止不依赖 ASR 和网络。

## 6. Silero VAD 算法

### 6.1 默认参数

| 参数 | 默认值 | 是否可配置 |
| --- | ---: | --- |
| 起始阈值 | `0.60` | 是 |
| 结束阈值 | `起始阈值 - 0.15`，默认 `0.45` | 自动派生，只读显示 |
| 起始确认时长 | `60ms` | 是 |
| 结束确认时长 | `300ms` | 是 |
| 前置缓存 | `300ms` | 是 |
| 最短有效语音 | `150ms` | 否，内部保护值 |
| 单句最长时长 | `8s` | 否，内部保护值 |
| Silero 窗口 | `512 samples`，16 kHz 下约 `32ms` | 否 |

时间条件按“至少达到配置时长”计算，并向 32ms 模型窗口对齐。因此默认 60ms 实际约 64ms，300ms 实际约 320ms。

### 6.2 状态机

```text
IDLE
  └─ 概率 >= startThreshold → START_CANDIDATE

START_CANDIDATE
  ├─ 连续达到 startDuration → SPEAKING，并补入 preRoll 音频
  └─ 概率跌回阈值以下      → IDLE

SPEAKING
  ├─ 概率 < endThreshold   → END_CANDIDATE
  ├─ 达到 8s               → 强制完成
  └─ 其他                   → 保持

END_CANDIDATE
  ├─ 概率 >= endThreshold  → SPEAKING，清零结束计时
  └─ 连续低于 endThreshold 达到 endDuration → 完成语句
```

完成后的语句若有效语音不足 150ms，则直接丢弃。环形缓冲、当前语句和 WAV 封装只存在于内存，请求结束后清除。

### 6.3 参数约束

- 起始阈值：`0.30～0.90`。
- 起始确认时长：`32～500ms`。
- 结束确认时长：`96～2000ms`。
- 前置缓存：`0～1000ms`。
- 结束阈值使用 `max(0.05, startThreshold - 0.15)`。
- 非法旧配置恢复为默认值，不阻止应用启动。

## 7. 网络与失败策略

### 7.1 请求超时

- 字节 ASR：连接超时 3 秒，总请求超时 8 秒。
- JEV：连接超时 3 秒，总请求超时 5 秒。
- 语音控制请求不自动重试，避免过时命令稍后突然执行。
- 任何超时、断网、HTTP 非成功状态、响应解析错误或服务端错误均显示简短状态并返回 `NO_ACTION`。

### 7.2 URL 验证

- 远程地址必须使用 `https://`。
- ASR 与 JEV URL 分开保存和测试。
- URL 中不得包含用户名、密码或片段标识。
- 自定义 URL 可以包含中转站路径和查询参数，应用按完整端点使用，不再自动拼接路径。
- 恢复默认会分别恢复字节和 JEV 官方端点。

### 7.3 中转兼容约定

- 中转站必须接受与对应上游一致的请求方法、请求头和正文，并返回兼容响应。
- 小刷不把 JEV Key 自动复用为中转站的其他鉴权字段。
- 中转站若需要额外自定义头，超出第一版范围。
- “测试连接”成功只表示端点和凭证可用，不保证后续每次请求成功。

## 8. 凭证与隐私

### 8.1 凭证保存

- Android 最低版本为 8.0，可直接使用 Android Keystore 生成不可导出的 AES-GCM 密钥。
- 字节 API Key 与 JEV Key 使用 AES-GCM 加密后写入私有 SharedPreferences。
- 每个密文使用独立随机 nonce，并保存密文版本，便于以后迁移。
- Resource ID、模型名、URL 与非敏感参数可保存在普通 SharedPreferences。
- 输入框使用密码样式；保存后仅显示“已配置”和末尾少量字符。
- 提供分别清除字节凭证、清除 JEV 凭证和清除全部云端配置的操作。
- 应用保持 `android:allowBackup="false"`。

Keystore 只能降低静态提取和普通备份风险，不能保证 Root、运行时注入或已攻陷设备上的长期密钥绝对安全。该风险在设置页中明确说明。

### 8.2 数据边界

- 本地关键词模式不发起任何网络请求。
- 字节云 ASR 只发送本地 VAD 完成后的短音频。
- JEV 只接收最终文本，不接收音频。
- 转写文本仅在当前会话内存和界面状态中短暂存在，不写文件、不进入日志。
- 网络错误信息不得包含凭证、完整请求头、音频或完整转写。
- 用户首次启用云端 ASR 时，界面明确说明音频会发送到所配置的服务 URL。
- JEV 默认域名和用户填写的中转站都是独立第三方服务；设置页显示实际目标域名，由用户决定是否发送文本。

## 9. 界面设计

### 9.1 主界面

主界面保留无障碍状态、监听按钮、停止按钮和评论位置设置，新增：

```text
语音识别
  ○ 本地关键词（离线、快速）
  ○ 字节云 ASR（准确、需要联网）

行为判断
  ○ 严格口令
  ○ JEV 智能判断

[字节 ASR 设置]
[JEV 设置]
[高级设置]
```

- 本地关键词模式下隐藏或禁用云端凭证提示和 JEV 选择。
- 监听期间锁定会改变引擎实例的设置；必须停止监听后修改。
- 开始云端监听前检查 ASR URL、API Key 和 Resource ID。
- 选择 JEV 时额外检查 JEV URL 与 Key。

### 9.2 字节 ASR 设置

- 服务 URL，默认 `https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash`。
- 鉴权方式切换：新版控制台 API Key / 旧版控制台 AppID + Access Token。
- 按鉴权方式显示 API Key，或 AppID + Access Token。
- Resource ID。
- “保存”按钮。
- “试录并测试”按钮：明确提示将录制并上传一段最多 3 秒的测试语音。
- “清除凭证”和“恢复默认 URL”。

### 9.3 JEV 设置

- 服务 URL，默认 `https://jevtypesafeai.com/api/v1/decide`。
- API Key。
- 模型名称，默认 `jev-1.13.0`。
- “保存”按钮。
- “测试连接”按钮：只发送内置示例文本，不发送用户语音或屏幕内容。
- “清除凭证”和“恢复默认 URL”。

### 9.4 高级设置

主界面默认只显示一个“高级设置”按钮。点开后显示：

- 起始阈值，默认 `0.60`。
- 自动计算的结束阈值，只读，默认 `0.45`。
- 起始确认时长，默认 `60ms`。
- 结束确认时长，默认 `300ms`。
- 前置缓存，默认 `300ms`。
- JEV 模型名和置信度阈值。
- “恢复高级设置默认值”。

设置页说明 Silero 以约 32ms 为一个判断窗口，实际时长会向上对齐。

## 10. 生命周期与状态展示

监听服务使用以下用户可见状态：

```text
正在加载本地模型
正在监听
检测到语音
正在识别
正在判断意图
正在执行
监听已停止
```

- 云端请求开始时保存当前服务 generation；回调时必须再次核对。
- 停止按钮立即取消 AudioRecord、ASR 请求和 JEV 请求。
- 息屏继续沿用当前行为：停止监听，解锁后不自动恢复。
- 网络状态变化不自动切换到本地模式，避免用户误以为自然语言仍被理解；仅显示失败并继续等待下一句话。
- 通知中显示当前模式，但不显示转写文本或密钥。

## 11. 视频外放与误触边界

Silero VAD 判断的是“是否存在人声”，无法可靠区分用户说话和抖音视频中的人声。第一版采取以下缓解措施：

- 保留“使用小刷＋口令”作为云端模式的可选本地门控。
- 明确建议佩戴耳机。
- 严格口令使用完整句匹配，不使用包含匹配。
- JEV 必须包含 `NO_ACTION`，并执行置信度阈值。
- 点赞使用更高阈值，低置信度不动作。
- 过短、过长、超时和重叠语句直接丢弃。

这些措施不能保证零误触。真实效果必须在目标红米手机、抖音外放、耳机和不同噪声环境中测试。

## 12. 依赖与产物

- 继续使用 sherpa-onnx 1.13.8 AAR。
- 新增官方 `silero_vad.onnx` 并在 `assets.lock.json` 中锁定文件名、来源和 SHA-256。
- 更新 `prepare_assets.py` 下载并验证 VAD 模型。
- 新增 OkHttp 作为字节 ASR 与 JEV HTTP 客户端。
- Manifest 增加 `INTERNET` 和 `ACCESS_NETWORK_STATE`；本地模式不使用网络。
- 第三方许可文档加入 Silero VAD 的 MIT 许可和来源。

## 13. 测试设计

### 13.1 纯逻辑测试

- VAD 概率连续 64ms 高于 0.60 后进入说话状态。
- 单个高概率尖峰不会开始语句。
- 说话中概率短暂低于 0.45 后恢复，不结束语句。
- 连续至少 300ms 低于 0.45 后完成语句。
- 150ms 以下的有效语音被丢弃。
- 8 秒达到上限时强制完成。
- 前置缓存长度和裁剪符合配置。
- 阈值迟滞公式和配置范围验证正确。
- 严格口令不会把“不要点赞”判断为 `LIKE`。
- JEV 未知行为、低置信度和损坏响应均得到 `NO_ACTION`。
- 云端结果超过新鲜度、监听已停止或 generation 改变时被拒绝。

### 13.2 协议测试

- 使用本地假服务器验证 ASR 二进制正文、鉴权头、超时、取消与响应解析。
- 使用本地假服务器验证 JEV `choice` 请求、固定选项和置信度解析。
- 验证自定义 URL 确实替换默认端点，ASR 与 JEV 不串用。
- 验证日志和错误文本不包含 API Key。
- 验证停止监听会取消所有在途请求，且回调不能执行动作。

### 13.3 Android 与真机测试

- Android Keystore 保存、读取、覆盖、清除和损坏密文恢复。
- 本地模式在断网条件下维持现有功能。
- 云端模式在 Wi-Fi、移动网络、无网、慢网和中转站条件下行为正确。
- 红米后台、切换抖音、通知停止、息屏停止和重新打开应用。
- 戴耳机与扬声器外放场景的 VAD 误触率。
- 普通话、短停顿、低声、远场与环境噪声下的分段效果。
- 字节严格口令链路和字节 + JEV 自然表达链路的端到端延迟。

## 14. 验收标准

- 首次安装和升级后默认仍为本地关键词模式，不发生网络请求。
- 本地关键词模式功能与当前版本一致。
- 云端模式只上传 VAD 完成的短语音，监听静音期间不上传。
- 用户能够分别修改、测试和恢复 ASR/JEV 服务 URL。
- 用户能够保存和清除自己的字节/JEV 凭证，仓库和 APK 中不包含实际凭证。
- 高级设置能够配置起始阈值、起始时长、结束时长和前置缓存，并正确显示派生结束阈值。
- 所有云端失败都以“不执行动作”结束。
- JEV 无法返回固定集合之外的可执行行为。
- 停止、锁屏、模式切换和过期结果都不能产生延迟动作。

## 15. 参考资料

- [火山引擎豆包语音文档](https://www.volcengine.com/docs/6561)
- [火山引擎双向流式 ASR 文档（用于协议与鉴权调研，第一版不采用持续流式）](https://www.volcengine.com/docs/DoubaoVoice/bidirectional-streaming-automatic-speech-recognition-websocket)
- [JEV Decision API](https://jevtypesafeai.com/zh/docs)
- [Silero VAD](https://github.com/snakers4/silero-vad)
- [sherpa-onnx Android VAD 示例](https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8/android/SherpaOnnxVad)
- [Android 麦克风前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone)
- [Android 后台启动前台服务限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
