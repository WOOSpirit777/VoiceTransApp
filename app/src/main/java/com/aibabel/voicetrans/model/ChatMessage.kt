package com.aibabel.voicetrans.model

class ChatMessage {
    var id: String = System.currentTimeMillis().toString()
        private set
    var sourceText: String? = null
    var targetText: String? = null
    var sourceLang: String? = null
    var targetLang: String? = null
    var timestamp: Long = System.currentTimeMillis()
        private set
    var isSending: Boolean = false
    var isError: Boolean = false
    var sourceAudioData: ByteArray? = null
    var ttsAudioData: ByteArray? = null

    constructor()

    constructor(sourceText: String, sourceLang: String) {
        this.sourceText = sourceText
        this.sourceLang = sourceLang
    }

    fun setId(id: String) {
        this.id = id
    }

    fun setTimestamp(timestamp: Long) {
        this.timestamp = timestamp
    }

    val sourceLangDisplay: String
        get() = when (sourceLang) {
            "zh" -> "中文"
            "id" -> "印尼语"
            else -> sourceLang ?: ""
        }

    val targetLangDisplay: String
        get() = when (targetLang) {
            "zh" -> "中文"
            "id" -> "印尼语"
            else -> targetLang ?: ""
        }
}
