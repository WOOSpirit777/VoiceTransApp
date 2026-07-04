package com.aibabel.voicetrans.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import com.aibabel.voicetrans.R
import com.aibabel.voicetrans.databinding.ActivityBroadcastUrlBinding
import com.aibabel.voicetrans.viewmodel.BroadcastUrlViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.quniu.baselib.activity.BaseVBActivityOpt

class BroadcastUrlActivity : BaseVBActivityOpt<BroadcastUrlViewModel, ActivityBroadcastUrlBinding>(
    inflate = { ActivityBroadcastUrlBinding.inflate(it) }
) {

    override fun initView() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnRefresh.setOnClickListener { viewModel.fetchBroadcastUrl() }
    }

    override fun initData() {
        viewModel.urlData.observe(this) { url ->
            binding.tvUrl.text = url
            val qrCodeBitmap = generateQrCode(url)
            binding.ivQrCode.setImageBitmap(qrCodeBitmap)
        }
        viewModel.fetchBroadcastUrl()
    }

    override fun initListener() {}

    override fun useEventBus(): Boolean = false

    private fun generateQrCode(content: String): Bitmap? {
        return try {
            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M

            val writer = QRCodeWriter()
            val matrix = writer.encode(content, BarcodeFormat.QR_CODE, 500, 500, hints)

            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: WriterException) {
            Log.e(TAG, "Failed to generate QR code: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "BroadcastUrlActivity"
    }
}