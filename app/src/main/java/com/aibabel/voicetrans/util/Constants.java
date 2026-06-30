package com.aibabel.voicetrans.util;

public class Constants {
    // WebSocket服务器地址
    public static final String WS_URL_CN = "ws://192.168.1.226:18050/speech/mode/ws_s2s";
    public static final String WS_URL_COM = "wss://joner-trans.aibabel.com/speech/mode/ws";
    
    public static final String WS_URL = WS_URL_CN;

    // 认证Token (Bearer token)
    public static final String ACCESS_TOKEN = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE3ODI0MzgxNDYsInN1YiI6Ikw2MDg3WUowMDAwMDAxNyJ9.XK0KfE3V9mDc1d8IVQ13DCFkTLjzOob9SGjJN1nXlk97JVRbdMFq2janlUt_5iT24raenigPWSw-dE6VwD_FaA";

    // 语言类型 (新接口使用 zh, en, id)
    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";
    public static final String LANG_ID = "id";

    // 音频参数
    public static final int SAMPLE_RATE = 16000;
    public static final int CHANNEL_CONFIG = 1;
    public static final int AUDIO_FORMAT = 16;
    public static final int CHUNK_SIZE = 3200;

    // 控制命令
    public static final String CMD_FINISH = "finish";
    public static final String CMD_CLOSE = "close";

    // 服务器响应
    public static final String RESP_PROCESSING_COMPLETED = "Processing completed and result sent";
    public static final String RESP_CONNECTION_CLOSING = "Connection closing";
    public static final String RESP_TRANSLATE_FINISH = "translate finish";

    // 消息类型
    public static final int MSG_TYPE_TEXT = 1;
    public static final int MSG_TYPE_BINARY = 2;

    // 结果类型
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_ERROR = "error";

    // 结果分类
    public static final String CLASS_SOURCE = "source";
    public static final String CLASS_TARGET = "target";
    public static final String CLASS_TTS = "tts";
}