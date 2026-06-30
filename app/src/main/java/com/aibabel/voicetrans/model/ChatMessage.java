package com.aibabel.voicetrans.model;

/**
 * 聊天消息实体
 */
public class ChatMessage {
    private String id;
    private String sourceText;      // 源语言文本
    private String targetText;      // 翻译后文本
    private String sourceLang;      // 源语言 zh_CN / en_US
    private String targetLang;      // 目标语言
    private long timestamp;
    private boolean isSending;      // 是否正在发送
    private boolean isError;        // 是否错误

    public ChatMessage() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.timestamp = System.currentTimeMillis();
    }

    public ChatMessage(String sourceText, String sourceLang) {
        this();
        this.sourceText = sourceText;
        this.sourceLang = sourceLang;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceText() {
        return sourceText;
    }

    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public String getTargetText() {
        return targetText;
    }

    public void setTargetText(String targetText) {
        this.targetText = targetText;
    }

    public String getSourceLang() {
        return sourceLang;
    }

    public void setSourceLang(String sourceLang) {
        this.sourceLang = sourceLang;
    }

    public String getTargetLang() {
        return targetLang;
    }

    public void setTargetLang(String targetLang) {
        this.targetLang = targetLang;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isSending() {
        return isSending;
    }

    public void setSending(boolean sending) {
        isSending = sending;
    }

    public boolean isError() {
        return isError;
    }

    public void setError(boolean error) {
        isError = error;
    }

    public String getSourceLangDisplay() {
        return "zh".equals(sourceLang) ? "中文" : "英文";
    }

    public String getTargetLangDisplay() {
        return "zh".equals(sourceLang) ? "英文" : "中文";
    }
}
