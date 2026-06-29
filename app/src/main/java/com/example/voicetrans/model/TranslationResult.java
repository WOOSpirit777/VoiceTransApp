package com.example.voicetrans.model;

import com.google.gson.annotations.SerializedName;

/**
 * WebSocket响应结果
 */
public class TranslationResult {

    @SerializedName("type")
    private String type;

    @SerializedName("class")
    private String classType;

    @SerializedName("text")
    private String text;

    @SerializedName("code")
    private String code;

    @SerializedName("message")
    private String message;

    @SerializedName("timestamp")
    private long timestamp;

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getClassType() {
        return classType;
    }

    public void setClassType(String classType) {
        this.classType = classType;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isResult() {
        return "result".equals(type);
    }

    public boolean isError() {
        return "error".equals(type);
    }

    public boolean isSource() {
        return "source".equals(classType);
    }

    public boolean isTarget() {
        return "target".equals(classType);
    }

    @Override
    public String toString() {
        return "TranslationResult{" +
                "type='" + type + '\'' +
                ", classType='" + classType + '\'' +
                ", text='" + text + '\'' +
                ", code='" + code + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
