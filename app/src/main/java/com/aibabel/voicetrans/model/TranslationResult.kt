package com.aibabel.voicetrans.model

import com.google.gson.annotations.SerializedName

/**
 * 翻译结果数据模型
 *
 * 对应服务端 WebSocket 消息的 JSON 结构：
 *
 * 1. 源语言识别文本：
 *    {"type":"text","class":"source","text":"你好","msg_id":"1","timestamp":"..."}
 *
 * 2. 目标语言翻译文本：
 *    {"type":"text","class":"target","text":"Halo","msg_id":"2","timestamp":"..."}
 *
 * 3. Base64 编码的 TTS 音频：
 *    {"type":"binary","class":"binary","text":"<base64>","msg_id":"3","timestamp":"..."}
 *
 * 4. 翻译结束标记：
 *    {"type":"text","class":"finish","text":"translate_finish","msg_id":"0","timestamp":"..."}
 */
class TranslationResult {

    @SerializedName("type")
    var type: String? = null

    @SerializedName("class")
    var classType: String? = null

    @SerializedName("text")
    var text: String? = null

    @SerializedName("code")
    var code: String? = null

    @SerializedName("message")
    var message: String? = null

    @SerializedName("timestamp")
    var timestamp: Long = 0

    @SerializedName("msg_id")
    var msgId: String? = null

    @SerializedName("session_id")
    var sessionId: String? = null

    val isText: Boolean
        get() = "text" == type

    val isError: Boolean
        get() = "error" == type

    val isSource: Boolean
        get() = "source" == classType

    val isTarget: Boolean
        get() = "target" == classType

    val isTts: Boolean
        get() = "tts" == classType

    val isTtsFinish: Boolean
        get() = isTts && "tts finish" == text

    override fun toString(): String {
        return "TranslationResult{" +
                "type='" + type + '\'' +
                ", classType='" + classType + '\'' +
                ", text='" + text + '\'' +
                ", code='" + code + '\'' +
                ", message='" + message + '\'' +
                '}'
    }
}
