package io.iskopasi.shader_test.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Picture
import android.media.Image
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

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

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Modifier.applyShader(shader: Shaders) = composed {
    if (shader.shaderHolder.animated) {
        var time by remember { mutableFloatStateOf(0f) }
//        val infiniteTransition = rememberInfiniteTransition("loop")
//        val animation = infiniteTransition.animateFloat(
//            label = "progress",
//            initialValue = 0f,
//            targetValue = 1f,
//            animationSpec = infiniteRepeatable(
//                tween(400, easing = LinearEasing),
//            )
//        )

        // Coroutine to simulate frame updates for the wavy animation
        LaunchedEffect("animation") {
            while (true) {
                delay(32) // Delay to simulate frame rate, adjust as needed
                time += 0.032f // Increase time by 0.016 seconds (60 FPS simulation)
            }
        }

        graphicsLayer {
            clip = true

            shader.shaderHolder.runtimeShader.setFloatUniform("iTime", time)
//                Log.e("-->>", "animationProgress ${animation.value}")
//                shader.shaderHolder.runtimeShader.setFloatUniform("progress", animation.value)

            renderEffect = shader.shaderHolder.compose().asComposeRenderEffect()
        }
    } else {
        graphicsLayer {
            clip = true
            renderEffect = shader.shaderHolder.compose().asComposeRenderEffect()
        }
    }
}

fun bg(block: () -> Unit): Job {
    return CoroutineScope(Dispatchers.IO).launch {
        block()
    }
}

fun Context.loadShader(filepath: String): String = assets
    .open(filepath)
    .bufferedReader().use {
        it.readText()
    }
    .trimIndent()

val String.e: String
    get() {
        Log.e("-->", this)
        return this
    }


fun Image.toBitmap(): Bitmap = planes[0].buffer.let {
    val bytes = ByteArray(it.remaining())
    it.get(bytes)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
}

fun Image.saveToFile(context: Context) = toBitmap().saveToFile(context)

fun Bitmap.saveToFile(context: Context) {
    // Add a specific media item.
    val resolver = context.contentResolver

    val imageStorageAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val imageDetails = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "shadertoy_${System.currentTimeMillis()}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis())
    }

    try {
        // Save the image.
        resolver.insert(imageStorageAddress, imageDetails)?.let { uri ->
            resolver.openOutputStream(uri)?.use { outStream ->
                val isBitmapCompressed =
                    compress(
                        Bitmap.CompressFormat.JPEG,
                        100,
                        outStream
                    )
                recycle()
            } ?: throw IOException("Failed to get output stream.")
        } ?: throw IOException("Failed to create new MediaStore record.")
    } catch (e: IOException) {
        throw e
    }
}