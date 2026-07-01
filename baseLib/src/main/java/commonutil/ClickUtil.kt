package commonutil

object ClickUtil {

    private var clickTime = 0L

    //防止重复点击
    fun canPerformNext(limitTime: Int = 1300): Boolean {
        if (System.currentTimeMillis() - clickTime > limitTime) {
            clickTime = System.currentTimeMillis()
            return true
        }
        return false
    }
}