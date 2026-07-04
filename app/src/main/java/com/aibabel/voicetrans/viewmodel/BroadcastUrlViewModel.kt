package com.aibabel.voicetrans.viewmodel

import NetClient
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.quniu.baselib.viewmodel.BaseViewModel
import netlib.util.ParamUtil
import okhttp3.RequestBody

class BroadcastUrlViewModel : BaseViewModel() {

    private val _urlData = MutableLiveData<String>()
    val urlData: LiveData<String> = _urlData

    fun fetchBroadcastUrl() {
        launchGo({
            val hashMap = HashMap<String, Any?>()
            val body: RequestBody = ParamUtil.getRequestBody(hashMap)
            val response = NetClient.create(ApiService::class.java).getBroadcastUrl(body)
            if (response.code == 0) {
                _urlData.postValue(response.data)
            }
        })
    }
}