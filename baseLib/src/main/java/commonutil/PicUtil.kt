package commonutil

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import android.util.TypedValue
import android.widget.ImageView
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.quniu.baselib.R
import java.io.File

/**
 * Created by Administrator on 2018/7/12.
 */
object PicUtil {
    fun loadLocalImage(
        context: Context?, imgResId: Int, imageView: ImageView,
        imgResIdDefault: Int
    ) {
        context?.let {
            if (context is Activity && (context.isFinishing || context.isDestroyed)) {
                return
            }
            Glide.with(it)
                .load(imgResId)
                .error(imgResIdDefault)
                .into(imageView)
        }
    }
    /**
     * 常规图片
     */
    fun loadImage(
        context: Context?, pathOrUrl: String?, imageView: ImageView
    ) {
        context?.let {
            if (context is Activity && (context.isFinishing || context.isDestroyed)) return
            if (pathOrUrl.isNullOrBlank()) {
                imageView.setImageDrawable(null)
                return
            }
            val s = pathOrUrl.trim()
            try {
                when {
                    s.startsWith("http://", true) || s.startsWith("https://", true) -> {
                        Glide.with(it).load(s).error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                    }
                    s.startsWith("content://", true) || s.startsWith("file://", true) -> {
                        val uri = s.toUri()
                        Glide.with(it).load(uri).error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                    }
                    else -> {
                        val f = File(s)
                        if (f.exists()) {
                            Glide.with(it).load(f).error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                        } else {
                            // Fallback: try to load as-is (could be url without scheme)
                            Glide.with(it).load(s).error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                        }
                    }
                }
            } catch (_: Exception) {
                // fallback to string load
                Glide.with(it).load(pathOrUrl).error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
            }
        }
    }

/**
 * 加载图片并指定宽高（dp）
 *
 * @param context 上下文
 * @param imgUrl 图片地址
 * @param imageView 显示图片的 ImageView
 * @param width 宽度（dp）
 * @param height 高度（dp）
 */
fun loadImageWithSize(
    context: Context?,
    imgUrl: String?,
    imageView: ImageView,
    width: Int,
    height: Int
) {
    context?.let {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) {
            return
        }

        val targetWidth = (width * Resources.getSystem().displayMetrics.density).toInt()
        val targetHeight = (height * Resources.getSystem().displayMetrics.density).toInt()

        Glide.with(it)
            .load(imgUrl)
            .override(targetWidth, targetHeight) // 指定图片加载的宽高
            .into(imageView)
    }
}



    /**
     * 常规图片
     */
    fun loadCircleImage(
        context: Context?, imgResId: Int, imageView: ImageView
    ) {
        context?.let {
            if (context is Activity && (context.isFinishing || context.isDestroyed)) {
                return
            }
            Glide.with(it)
                .load(imgResId)
                .apply(RequestOptions.bitmapTransform(CircleCrop()))
                .into(imageView)
        }
    }

    /**
     * 合并后的 GIF 加载方法：接受 Int(resId)、String(url/path)、Uri、File 等通用类型
     */
    fun loadGifImage(context: Context?, source: Any?, imageView: ImageView) {
        context?.let {
            if (context is Activity && (context.isFinishing || context.isDestroyed)) {
                return
            }
            try {
                when (source) {
                    is Int -> Glide.with(it).asGif().load(source).into(imageView)
                    is String -> Glide.with(it).asGif().load(source).into(imageView)
                    is Uri -> Glide.with(it).asGif().load(source).into(imageView)
                    is File -> Glide.with(it).asGif().load(source).into(imageView)
                    else -> Glide.with(it).asGif().load(source).into(imageView)
                }
            } catch (_: Exception) {
                Glide.with(it).asGif().load(source?.toString()).into(imageView)
            }
        }
    }


    /**
     * 圆形图片
     */
    fun loadCircleImage(
        context: Context?, imgUrl: String?, imageView: ImageView
    ) {
        context?.let {
            if (context is Activity && (context.isFinishing || context.isDestroyed)) {
                return
            }
            Glide.with(it)
                .load(imgUrl)
                .apply(RequestOptions.bitmapTransform(CircleCrop()))
                .into(imageView)
        }
    }

    private val Float.changeToDp
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this,
            Resources.getSystem().displayMetrics
        )
    enum class PicRoundType {
        ROUND_ALL, ROUND_TOP, ROUND_BOTTOM, ROUND_LEFT, ROUND_RIGHT, ONLY_LEFT_TOP, ONLY_RIGHT_TOP, ONLY_BOTTOM_LEFT, ONLY_BOTTOM_RIGHT
    }

    // 新增：返回统一的圆角变换
    private fun getRoundTransformation(corner: Float, roundType: PicRoundType): BitmapTransformation {
        val px = corner.changeToDp
        return when (roundType) {
            PicRoundType.ROUND_ALL -> RoundedCorners(px.toInt())
            PicRoundType.ROUND_TOP -> GranularRoundedCorners(px, px, 0f, 0f)
            PicRoundType.ROUND_BOTTOM -> GranularRoundedCorners(0f, 0f, px, px)
            PicRoundType.ROUND_LEFT -> GranularRoundedCorners(px, 0f, 0f, px)
            PicRoundType.ROUND_RIGHT -> GranularRoundedCorners(0f, px, px, 0f)
            PicRoundType.ONLY_LEFT_TOP -> GranularRoundedCorners(px, 0f, 0f, 0f)
            PicRoundType.ONLY_BOTTOM_LEFT -> GranularRoundedCorners(0f, 0f, 0f, px)
            PicRoundType.ONLY_RIGHT_TOP -> GranularRoundedCorners(0f, px, 0f, 0f)
            PicRoundType.ONLY_BOTTOM_RIGHT -> GranularRoundedCorners(0f, 0f, px, 0f)
        }
    }

    /**
     * 合并后的圆角图片加载方法：接受 Int(resId)、String(url/path)、File、Uri、Bitmap 等多种 source
     */
    fun loadRoundImage(
        context: Context?,
        source: Any?,
        imageView: ImageView,
        corner: Float = 10F,
        roundType: PicRoundType = PicRoundType.ROUND_ALL
    ) {
        context?.let {
            if (context is Activity && (context.isFinishing || context.isDestroyed)) {
                return
            }

            // 当 source 是 String 且为空白时，清空 ImageView
            if (source is String && source.isBlank()) {
                imageView.setImageDrawable(null)
                return
            }

            try {
                when (source) {
                    is Int -> {
                        if (imageView.scaleType == ImageView.ScaleType.CENTER_CROP) {
                            Glide.with(it).load(source)
                                .transform(CenterCrop(), getRoundTransformation(corner, roundType))
                                .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                        } else {
                            Glide.with(it).load(source)
                                .transform(getRoundTransformation(corner, roundType))
                                .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                        }
                    }
                    is File -> {
                        if (imageView.scaleType == ImageView.ScaleType.CENTER_CROP) {
                            Glide.with(it).load(source)
                                .transform(CenterCrop(), getRoundTransformation(corner, roundType))
                                .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                        } else {
                            Glide.with(it).load(source)
                                .transform(getRoundTransformation(corner, roundType))
                                .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder)
                                .into(imageView)
                        }
                    }
                    is Uri -> {
                        if (imageView.scaleType == ImageView.ScaleType.CENTER_CROP) {
                            Glide.with(it).load(source)
                                .transform(CenterCrop(), getRoundTransformation(corner, roundType))
                                .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder)
                                .into(imageView)
                        } else {
                            Glide.with(it).load(source)
                                .transform(getRoundTransformation(corner, roundType))
                                .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder)
                                .into(imageView)
                        }
                    }
                    is Bitmap -> {
                        if (imageView.scaleType == ImageView.ScaleType.CENTER_CROP) {
                            Glide.with(it).load(source)
                                .transform(CenterCrop(), getRoundTransformation(corner, roundType))
                                .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder)
                                .into(imageView)
                        } else {
                            Glide.with(it).load(source)
                                .transform(getRoundTransformation(corner, roundType))
                                .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder)
                                .into(imageView)
                        }
                    }
                    is String -> {
                        val s = source.trim()
                        try {
                            when {
                                s.startsWith("http://", true) || s.startsWith("https://", true) -> {
                                    // 网络图片
                                    if (imageView.scaleType == ImageView.ScaleType.CENTER_CROP) {
                                        Glide.with(it).load(s)
                                            .transform(CenterCrop(), getRoundTransformation(corner, roundType))
                                            .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                                    } else {
                                        Glide.with(it).load(s)
                                            .transform(getRoundTransformation(corner, roundType))
                                            .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                                    }
                                }
                                s.startsWith("content://", true) || s.startsWith("file://", true) -> {
                                    // content 或 file URI
                                    val uri = s.toUri()
                                    loadRoundImage(context, uri, imageView, corner, roundType)
                                }
                                else -> {
                                    // 当作本地文件路径
                                    val f = File(s)
                                    if (f.exists()) {
                                        loadRoundImage(context, f, imageView, corner, roundType)
                                    } else {
                                        // 回退：当作网络 url 处理（兼容没有协议的情况）
                                        if (imageView.scaleType == ImageView.ScaleType.CENTER_CROP) {
                                            Glide.with(it).load(s)
                                                .transform(CenterCrop(), getRoundTransformation(corner, roundType))
                                                .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                                        } else {
                                            Glide.with(it).load(s)
                                                .transform(getRoundTransformation(corner, roundType))
                                                .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // 兜底：直接按字符串尝试加载
                            if (imageView.scaleType == ImageView.ScaleType.CENTER_CROP) {
                                Glide.with(it).load(source)
                                    .transform(CenterCrop(), getRoundTransformation(corner, roundType))
                                    .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                            } else {
                                Glide.with(it).load(source)
                                    .transform(getRoundTransformation(corner, roundType))
                                    .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                            }
                        }
                    }
                    else -> {
                        // 默认行为：把 source 转为字符串或直接交给 Glide
                        if (imageView.scaleType == ImageView.ScaleType.CENTER_CROP) {
                            Glide.with(it).load(source)
                                .transform(CenterCrop(), getRoundTransformation(corner, roundType))
                                .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                        } else {
                            Glide.with(it).load(source)
                                .transform(getRoundTransformation(corner, roundType))
                                .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                        }
                    }
                }
            } catch (_: Exception) {
                // 兜底：直接按字符串尝试加载
                if (imageView.scaleType == ImageView.ScaleType.CENTER_CROP) {
                    Glide.with(it).load(source)
                        .transform(CenterCrop(), getRoundTransformation(corner, roundType))
                        .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                } else {
                    Glide.with(it)
                        .load(source).transform(getRoundTransformation(corner, roundType))
                        .error(R.drawable.ic_placeholder).placeholder(R.drawable.ic_placeholder).into(imageView)
                }
            }
        }
    }
}