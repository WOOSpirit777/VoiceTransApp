package netlib.response

/**
 * @ClassName IBaseResponse
 * @Description TODO
 */
interface IBaseResponse<T> {
    fun status(): Int
    fun msg(): String
    fun data(): T
    fun isSuccess(): Boolean
}

interface IBaseResponseWithOutData {
    fun status(): Int
    fun msg(): String
    fun isSuccess(): Boolean
}