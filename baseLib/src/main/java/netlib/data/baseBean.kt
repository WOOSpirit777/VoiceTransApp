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

