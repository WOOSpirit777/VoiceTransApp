package netlib.exception

/**
 *   @auther : max
 *   time   : 2019/08/12
 */
enum class ERROR(private val code: Int, private val err: String) {

    /**
     * 未知错误
     */
    UNKNOWN(1000, "未知错误"),

    /**
     * 解析错误
     */
    PARSE_ERROR(1001, "数据解析错误"),

    /**
     * 网络错误
     */
    NETWORD_ERROR(1002, "网络连接超时，请稍后重试"),

    /**
     * 协议出错
     */
    HTTP_ERROR(1003, "Agreement Error"),

    /**
     * 证书出错
     */
    SSL_ERROR(1004, "验证失败，请尝试修改系统时间或更换网络环境"),

    /**
     * 连接超时
     */
    TIMEOUT_ERROR(1006, "网络连接超时，请稍后重试"),

    /**
     * 用户信息无效
     */
    MISSING_ERROR(400, "提交的信息有误"),

    /**
     * 用户信息无效
     */
    INVALID_USER_ERROR(401, "用户信息无效"),

    /**
     * 用户信息无效
     */
    ERROR_402(402, "用户信息无效"),

    /**
     * 用户信息无效
     */
    ERROR_403(403, "没有对应的权限"),

    /**
     * 用户信息无效
     */
    ERROR_404(404, "指定的信息不存在"),

    /**
     * 用户信息无效
     */
    FORBIDDEN_ERROR(405, "指定的信息不允许访问"),

    /**
     * 删除失败
     */
    DEL_ERROR(406, "该归集下存在任务,不能删除");


    fun getValue(): String {
        return err
    }

    fun getKey(): Int {
        return code
    }

}