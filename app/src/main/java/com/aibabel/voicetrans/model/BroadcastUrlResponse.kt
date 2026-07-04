package com.aibabel.voicetrans.model

data class BroadcastUrlResponse(
    val code: Int,
    val err_msg: String,
    val msg: String,
    val time: Long,
    val task_id: String,
    val data: String
)