package io.iskopasi.shader_test.utils

import android.graphics.Bitmap
import android.graphics.Picture
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

val Picture.toBitmap: Bitmap
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Bitmap.createBitmap(this)
        } else {
            val bitmap = Bitmap.createBitmap(
                this.width,
                this.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawPicture(this)
            bitmap
        }
    }

@Composable
fun Dp.toPx(): Float {
    with(LocalDensity.current) {
        return toPx()
    }
}