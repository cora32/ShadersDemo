package io.iskopasi.shader_test.utils

import android.graphics.Bitmap
import android.graphics.Picture
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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

@Stable
fun Modifier.screenshot(mutableStateHolder: MutableState<Bitmap>) =
    this.drawWithCache {
        val width = this.size.width.toInt()
        val height = this.size.height.toInt()
        val picture = Picture()

        onDrawWithContent {
            val pictureCanvas =
                Canvas(
                    picture
                        .beginRecording(
                            width,
                            height
                        )
                )

            draw(this, this.layoutDirection, pictureCanvas, size) {
                this@onDrawWithContent.drawContent()
            }
            picture.endRecording()

            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawPicture(picture)
                mutableStateHolder.value = picture.toBitmap
            }
        }
    }