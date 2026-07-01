package netlib.request

/**
 * @ClassName BaseRequest
 * @Description TODO
 */
class BaseRequest<T>(private val options: T) : IBaseRequest<T> {
    override fun options() = options
    override fun toString(): String {
        return "BaseRequest(options=$options)"
    }
}