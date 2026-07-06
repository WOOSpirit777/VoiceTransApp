package com.aibabel.voicetrans.util

import commonutil.ContentProviderUtil

object Constants {
    // WebSocket 服务地址
//    const val WS_URL_CN = "ws://192.168.1.226:18050/speech/mode/ws_s2s"
    const val WS_URL_CN = "wss://joner-trans.aibabel.cn/speech/mode/ws_s2s"
    const val WS_URL_COM = "wss://joner-trans.aibabel.com/speech/mode/ws_s2s"
    
     var WS_URL = if (ContentProviderUtil.isInCN()) WS_URL_CN else WS_URL_COM

    // 鉴权 Token
    const val ACCESS_TOKEN = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE3ODI0MzgxNDYsInN1YiI6Ikw2MDg3WUowMDAwMDAxNyJ9.XK0KfE3V9mDc1d8IVQ13DCFkTLjzOob9SGjJN1nXlk97JVRbdMFq2janlUt_5iT24raenigPWSw-dE6VwD_FaA"

    // 语言代码
    const val LANG_ZH = "zh"
    const val LANG_EN = "en"
    const val LANG_ID = "id"

    // 音频参数
    const val SAMPLE_RATE = 16000
    const val CHANNEL_CONFIG = 1
    const val AUDIO_FORMAT = 16
    const val CHUNK_SIZE = 3200

    // 客户端发送的控制命令
    const val CMD_FINISH = "finish"
    const val CMD_CLOSE = "close"
    const val CMD_CONNECT = "connect"

    // 服务端响应文本
    const val RESP_PROCESSING_COMPLETED = "Processing completed and result sent"
    const val RESP_CONNECTION_CLOSING = "Connection closing"
    const val RESP_TRANSLATE_FINISH = "translate finish"

    // 消息类型
    const val MSG_TYPE_TEXT = 1
    const val MSG_TYPE_BINARY = 2

    const val TYPE_TEXT = "text"
    const val TYPE_ERROR = "error"

    const val CLASS_SOURCE = "source"
    const val CLASS_TARGET = "target"
    const val CLASS_TTS = "tts"

    // 翻译结束超时时间（毫秒）
    // 发送 finish 后等待服务端 finish 响应，超时后发送 msg_id 重试
    const val TRANSLATE_FINISH_TIMEOUT_MS = 5000L

    // 会话模式
    const val MODE_S2S = "s2s"   // 交流模式：双按钮 push-to-talk
    const val MODE_S2T = "s2t"   // 同声传译：单按钮 + 语种选择
}
