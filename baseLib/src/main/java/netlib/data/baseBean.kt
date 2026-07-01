package netlib.data

//通用结果
data class CommonResult(val encryptData: String)

// 用于普通数据
data class TokenData(
    val token: String,
    val token_type: String,
    val serverTime: Long,
)

data class VersionData(
    val is_update: Boolean,
    val is_card_key_bind: Boolean,
    val url: String
)
data class InitEdu(
    val sn: Boolean,
    val imei1: Boolean,
    val imei2: Boolean,
    val mac: Boolean,
    val eid: Boolean,

)
data class Threshold(
    val rate_limiting: String="",
    val speech_count: Int
)

data class EsimStatus(
    val status: Int
)

data class AIInfo(
    /*{
        "apiKey": "sk-0751898402344a3081f10caf2132d094",
        "appid": "83ae48efb92645deb6d979a2fda11269",
        "workspaceId": "llm-s9ykz4zkrd9zah1k"
    }*/
    val apiKey: String,
    val appid: String,
    val workspaceId: String,
)
