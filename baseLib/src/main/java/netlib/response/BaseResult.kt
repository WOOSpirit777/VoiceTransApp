package netlib.response

/**
 * @ClassName BaseResult
 * @Description 网络请求返回数据
 * @Author Vvv
 */
data class BaseResult<T>(
    val msg: String,
    val status: Int,
    val code: Int,
    val time: Long,
    val task_id: String,
    val data: T,
    val success: Boolean = false
) : IBaseResponse<T> {
    override fun status() = status

    override fun msg() = msg

    override fun data() = data

    override fun isSuccess() = status == 200 || code == 0 || success

}

data class BaseResultWithOutData(
    val msg: String,
    val status: Int,
    val code: Int,
    val success: Boolean = false
) : IBaseResponseWithOutData {
    override fun status() = status

    override fun msg() = msg

    override fun isSuccess() = status == 200 || code == 200 || success

}