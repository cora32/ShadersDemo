package io.iskopasi.shader_test.utils

import android.graphics.Bitmap
import android.graphics.Picture

val Picture.toBitmap: Bitmap
    get() {
        val bitmap = Bitmap.createBitmap(
            this.width,
            this.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawPicture(this)
        return bitmap
    }