package com.aibabel.voicetrans.model

import com.google.gson.annotations.SerializedName

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
