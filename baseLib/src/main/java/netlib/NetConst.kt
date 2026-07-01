package netlib

import com.quniu.baselib.util.MMKVConst
import com.quniu.baselib.util.MMKVUtils

object NetConst {
    var REG_ID: String = MMKVUtils.get(MMKVConst.REG_ID,"") as String
    var TOKEN: String = MMKVUtils.getMultiStr(MMKVConst.TOKEN, MMKVConst.MMKV_ID)
    var TOKEN_TYPE: String =  MMKVUtils.getMultiStr(MMKVConst.TOKEN_TYPE, MMKVConst.MMKV_ID)
//    var BASE_URL2 = "https://joner-trans.aibabel.com" //生产网关地址
    var BASE_URL2 = "https://joner-trans.aibabel.com" //生产网关地址
    var CN_BASE_URL = "https://joner-trans.aibabel.cn" //中国环境
    var GLOBAL_BASE_URL = "https://joner-trans.aibabel.com" //国外环境
    const val API_TAG_SPEECH = "/speech"
    const val API_TAG_SCENIC = "/scenic"
    const val API_TAG_GLOBAL = "/global"
    const val API_REGISTER = "/v1/register" //注册接口
    const val API_SERVER_TIME = "/v1/serverTime" //注册接口
    const val API_SCENE_LIST = "/v1/scene/list" //根据城市获取景列表
    const val API_SCENE_BY_ID = "/v1/scene/id" //07.根据景区ID获取景区信息
    const val API_SUBSCENE_BY_ID = "/v1/subScene/list" //08.根据景区ID获取子景点列表
    const val API_SEARCH = "/v1/search/list" //09.搜索接口
    const val API_SEARCH_V2 = "/v1/search/list/v2" //09.搜索接口v2
    const val API_COLLECT_LIST = "/v1/collect/list" //10.获取收藏列表
    const val API_NEARBY_LIST = "/v1/nearby/list" //12.获取附近列表
    const val API_COLLECT_CHANGE = "/v1/collect/change" //11.添加/取消收藏
    const val API_OFFLINE_LIST = "/v1/offline/list" //13.离线包列表

    const val API_DETAIL = "/global/v1/detail"//03.eSIM卡详情查询
    const val API_ACTIVE = "/global/v1/active"//04.eSIM卡激活
    const val API_REPORT_FLOW = "/global/v1/report_flow"//12.接收上报eSim卡流量使用记录
    const val API_PAY= "/global/v1/pay"//06.发起支付
    const val API_PAY_RESULT = "/global/v1/pay_result"//07.支付状态查询接口（客户端轮询）
    const val API_ORDER_LIST = "/global/v1/order_list"//08.订单列表页
    const val API_ORDER_CALLBACK = "/global/v1/finish_order"//09.通知服务端是否下载成功完成订单
    const val API_USE_CARD_KEY = "/global/v1/card_key"//13.密码卡card_key校验
}