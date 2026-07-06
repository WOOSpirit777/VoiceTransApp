# VoiceTransApp - 语音翻译应用

基于 WebSocket 的实时语音翻译 Android 应用，支持主副双屏显示、交流模式与同声传译模式切换、物理按键操作。

## 功能特性

-  **实时语音翻译**：通过 WebSocket 实时传输音频数据，服务端返回翻译文本和 TTS 音频
-  **双模式切换**：
   - 交流模式（s2s）：两个圆形按钮，长按录音、松开结束
   - 同声传译（s2t）：单录音按钮 + 语种选择
-  **主副双屏**：主屏和副屏（HDMI 外接）显示不同语种，Presentation 实现
-  **物理按键支持**：按键 134（左）/ 135（右）对应左右按钮，push-to-talk 操作
-  **断线重连**：异常断开后自动重连，保留 session_id 和最后一条消息的 msg_id
-  **超时重试**：发送 finish 后 5 秒未收到响应，发送最后一条 msg_id 重试，最多 2 次
-  **TTS 自动播放**：可配置是否自动播放翻译后的音频
-  **音频持久化**：录音 PCM 和 TTS 音频自动保存到设备存储

## 技术架构

```
UI 层 (MainActivity / SettingsActivity)
    ↓
TranslateHelper (翻译流程管理器)
    ├── AudioRecorderManager (录音管理)
    ├── AudioPlayerManager   (播放管理)
    ├── WebSocketManager     (WebSocket 通信)
    ├── AudioSendManager     (音频发送队列)
    └── Room Database        (本地数据持久化)
```

## 项目结构

```
app/src/main/java/com/aibabel/voicetrans/
├── audio/                          # 音频相关
│   ├── AudioRecorderManager.kt     # 录音管理器（PCM 16kHz 16bit）
│   └── AudioPlayerManager.kt       # 播放器管理器（流式/完整播放）
├── data/                           # 数据层
│   ├── AppDatabase.kt              # Room 数据库
│   ├── AudioSegment.kt             # 音频分段实体
│   ├── AudioSegmentDao.kt          # 音频分段 DAO
│   ├── AudioSegmentRepository.kt   # 音频分段仓储
│   ├── AudioSendManager.kt         # 音频发送管理器
│   ├── TranslationSession.kt       # 翻译会话实体
│   ├── TranslationSessionDao.kt    # 翻译会话 DAO
│   └── TranslationSessionRepository.kt
├── model/                          # 数据模型
│   ├── ChatMessage.kt              # 聊天消息
│   ├── TranslationResult.kt        # 翻译结果（WebSocket 消息结构）
│   └── BroadcastUrlResponse.kt
├── ui/                             # 界面层
│   ├── MainActivity.kt             # 主界面
│   ├── SettingsActivity.kt         # 设置页
│   ├── TranslateHelper.kt          # 翻译流程核心
│   ├── ChatAdapter.kt              # 聊天列表适配器
│   ├── AudioButtonView.kt          # 录音按钮自定义 View
│   ├── SecondaryDisplayManager.kt  # 副屏管理器
│   ├── SecondaryPresentation.kt    # 副屏 Presentation
│   ├── BroadcastUrlActivity.kt
│   └── UpdateDialog.kt
├── util/Constants.kt               # 常量定义
├── utils/                          # 工具类
│   ├── LanguageUtils.kt            # 多语言工具
│   ├── SoundWaveView.kt            # 声波动画 View
│   └── DBCalculator.kt
├── viewmodel/                      # ViewModel
│   ├── MainViewModel.kt
│   ├── SettingsViewModel.kt
│   ├── BroadcastUrlViewModel.kt
│   ├── CommonViewModel.kt
│   └── ApiService.kt
└── websocket/
    └── WebSocketManager.kt         # WebSocket 单例管理器
```

## WebSocket 消息协议

### 连接 Header

| Header | 说明 | 示例 |
|---|---|---|
| Authorization | Bearer Token | `Bearer xxx` |
| langSource | 源语言 | `zh` / `id` |
| langTarget | 目标语言 | `id` / `zh` |
| sessionId | 会话 ID | UUID 字符串 |
| mode | 会话模式 | `s2s` / `s2t` |

### 服务端消息格式

所有服务端消息均为 JSON 格式，使用文本帧传输：

```json
{
  "type": "text",
  "class": "source",
  "text": "你好",
  "msg_id": "1",
  "timestamp": "1783322896",
  "session_id": "xxx"
}
```

#### 消息类型分类

| type | class | 说明 |
|---|---|---|
| `text` | `source` | 源语言识别文本 |
| `text` | `target` | 目标语言翻译文本 |
| `text` | `finish` | 翻译结束（替代 `translate finish`） |
| `binary` | `binary` | Base64 编码的 TTS 音频数据 |

#### Base64 音频消息

```json
{
  "type": "binary",
  "class": "binary",
  "text": "Base64编码的PCM音频数据",
  "msg_id": "123",
  "timestamp": "1783322896",
  "session_id": "xxx"
}
```

客户端收到后 Base64 解码为 `ByteArray`，走原有音频数据流（缓冲 + 流式播放 + 存库）。

#### 翻译结束消息

```json
{
  "msg_id": "0",
  "type": "text",
  "class": "finish",
  "text": "translate_finish",
  "timestamp": "1783324682",
  "session_id": "xxx"
}
```

### 客户端发送

- 音频数据：通过二进制帧发送 PCM 数据
- 控制命令：纯文本帧
  - `finish` — 录音结束，请求翻译完成
  - `close` — 关闭连接

## 重连与超时机制

### 异常断开重连

1. 发送 `finish` 后标记 `isPendingFinish = true`
2. 若 `onFailure` 或 `onClosed` 时 `isPendingFinish` 为 true，自动触发重连
3. 重连时使用上次的 `session_id`（不变更 UUID）
4. 重连成功后立即发送最后一条消息的 `msg_id`
5. 指数退避：1s → 2s → 4s → 8s，最多 5 次

### Finish 超时重试

```
停止录制 → 发送 finish → 启动 5 秒计时
        ↓
5 秒后未收到 finish → 发送最后一条 msg_id
        ↓
3 秒后未收到 → 重试（最多 2 次）
        ↓
2 次后仍未收到 → 手动结束对话
```

日志标记：
- `[TranslateFinishTimeout]` — 5 秒超时触发
- `[MsgIdRetry]` — msg_id 重试
- `[ManualFinish]` — 手动结束对话

## 主副屏语言规则

- 副屏显示语言在设置页切换（中文 / 印尼语）
- 主屏显示语言与副屏相反
- 切换副屏语言时通过广播 `ACTION_SECONDARY_MODE_CHANGED` 通知更新
- 印尼语 locale 代码：Android 使用 `in`（不是 `id`），资源目录 `values-in`

## 交流模式按钮映射

| 物理按键 | 屏幕按钮 | 语种 | langSource | langTarget |
|---|---|---|---|---|
| 134（左） | btnMainScreen | 主屏语言 | 主屏语种 | 副屏语种 |
| 135（右） | btnSecondaryScreen | 副屏语言 | 副屏语种 | 主屏语种 |

## 音频格式

- 采样率：16000 Hz
- 位深：16 bit PCM
- 声道：单声道
- 分段大小：3200 字节（200ms）

## 开发环境

- 语言：Kotlin
- 最低 SDK：21
- 目标 SDK：34
- 构建工具：Gradle + AGP 8.2.0
- 架构：MVVM + 单例管理 + Room
- 网络：OkHttp + WebSocket
- 数据绑定：DataBinding

## 快速开始

```bash
# 编译
./gradlew assembleDebug

# 安装并启动（设备已连接）
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.aibabel.voicetrans/.ui.MainActivity
```

或使用项目内置脚本：
- `run.bat` — 编译 + 安装 + 启动
- `start.bat` — 直接启动已安装的应用

## 配置项

在 `SharedPreferences` 中存储（文件名：`settings`）：

| Key | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `auto_play_tts` | Boolean | true | 是否自动播放 TTS 音频 |
| `secondary_screen_mode` | Int | 1（印尼语） | 副屏显示语言 |
| `session_mode` | String | s2s | 会话模式：s2s 交流 / s2t 同传 |

## 注意事项

1. WebSocket 二进制音频回调在 OkHttp 线程直接处理，不走主线程，避免消息积压
2. 录音数据保存路径：`/storage/emulated/0/Music/VoiceTrans_{lang}_{timestamp}.pcm`
3. Room 数据库版本升级使用 `fallbackToDestructiveMigration`
4. 录音 60 秒自动停止
