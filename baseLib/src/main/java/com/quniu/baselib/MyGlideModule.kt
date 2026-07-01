package com.quniu.baselib

import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache

@GlideModule
class MyGlideModule : AppGlideModule() {
    override fun applyOptions(context: android.content.Context, builder: GlideBuilder) {
        // 设置内存缓存大小为 50MB
        builder.setMemoryCache(LruResourceCache(50 * 1024 * 1024))
        // 设置磁盘缓存大小为 500MB
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, 500L * 1024 * 1024))
    }
}
