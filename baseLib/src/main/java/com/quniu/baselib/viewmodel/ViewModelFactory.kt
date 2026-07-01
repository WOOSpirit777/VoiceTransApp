package com.quniu.baselib.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider


/**
 * @ClassName ViewModelFactory
 * @Description ViewModel工厂
 * @Author Vvv
 */
class ViewModelFactory : ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return modelClass.newInstance()
    }
}