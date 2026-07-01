package com.aibabel.voicetrans.viewmodel

import netlib.NetConst
import netlib.data.TokenData
import netlib.data.VersionData
import netlib.response.BaseResult
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface ApiService {
    /**
     * App初始化
     */

    //下载文件
    @GET
    suspend fun downloadFile(@Url url: String): ResponseBody

    @POST(NetConst.API_TAG_SCENIC+ NetConst.API_REGISTER)
    suspend fun registerToken(
        @Header("AuthorizationKey") header: String,
        @Body body: RequestBody
    ): BaseResult<TokenData>



    @POST(NetConst.API_TAG_SPEECH+"/mode/check/version2")
    suspend fun checkVersion(
        @Body body: RequestBody
    ): BaseResult<VersionData>


}