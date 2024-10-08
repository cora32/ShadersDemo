package io.iskopasi.shader_test.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Picture
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.media.ExifInterface
import android.media.Image
import android.os.Build
import android.os.FileUtils
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import android.view.WindowManager
import android.webkit.MimeTypeMap
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.iskopasi.shader_test.BuildConfig
import io.iskopasi.shader_test.utils.RealPathUtil.getRealPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


val Picture.toBitmap: Bitmap
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Bitmap.createBitmap(this)
        } else {
            val bitmap = Bitmap.createBitmap(
                this.width, this.height, Bitmap.Config.ARGB_8888
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
fun Modifier.screenshot(mutableStateHolder: MutableState<Bitmap>) = this.drawWithCache {
    val width = this.size.width.toInt()
    val height = this.size.height.toInt()
    val picture = Picture()

    onDrawWithContent {
        val pictureCanvas = Canvas(
            picture.beginRecording(
                width, height
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

fun Modifier.applyShader(shader: Shaders) = composed {
    if (shader.shaderHolder.animated) {
        var time by remember { mutableFloatStateOf(0f) }
        var shaderEffect =

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

//            "---> Applying shader: ${shader.shaderName}".e
            shader.shaderHolder.runtimeShader.setFloatUniform("iTime", time)
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

fun bg(block: suspend (CoroutineScope) -> Unit): Job = CoroutineScope(Dispatchers.IO).launch {
    block(this)
}

fun main(block: suspend CoroutineScope.() -> Unit): Job = CoroutineScope(Dispatchers.Main).launch {
    block(this)
}

fun Context.loadShader(filepath: String): String = assets.open(filepath).bufferedReader().use {
        it.readText()
}.trimIndent()

val String.e: String
    get() {
        Log.e("-->", this)
        return this
    }


fun checkPermissions(context: Context) = ContextCompat.checkSelfPermission(
    context, Manifest.permission.CAMERA
) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
    context, Manifest.permission.RECORD_AUDIO
) == PackageManager.PERMISSION_GRANTED
//        && ContextCompat.checkSelfPermission(
//    context,
//    Manifest.permission.WRITE_EXTERNAL_STORAGE
//) == PackageManager.PERMISSION_GRANTED

fun Image.toBitmap(mode: Int = 0): Bitmap = when (mode) {
    0 ->
        planes[0].buffer.let {
            val bytes = ByteArray(it.remaining())
            it.get(bytes)
            // decodeByteArray ignores the EXIF flag
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
        }

    1 -> {
            planes[0].buffer.let {
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding: Int = rowStride - pixelStride * width

                Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                ).apply {
                    it.rewind()
                    copyPixelsFromBuffer(it)
                }
            }
    }

    else -> throw Exception("toBitmap: Unknown mode")
}

fun Image.saveToFile(context: Context) = toBitmap(0).saveBmpToFile(context)

fun Image.saveARGB8888ToFile(context: Context) = toBitmap(1).saveBmpToFile(context)

fun File.copyToDcim(context: Context): File? {
    val storageAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val filename = "shadertoy_${System.currentTimeMillis()}.${this@copyToDcim.extension}"
    val details = ContentValues().apply {
        put(
            MediaStore.Video.Media.DISPLAY_NAME,
            filename
        )
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis())
    }

    context.contentResolver.apply {
        insert(storageAddress, details)?.let { uri ->
            openOutputStream(uri)?.use { outStream ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    this@copyToDcim.inputStream().use {
                        FileUtils.copy(it, outStream)
                    }
                } else {
                    outStream.write(this@copyToDcim.readBytes())
                }

//                this@saveToDcim.delete()

                return File(getRealPath(context, uri)!!).apply {
                    "--> Saved to DCIM ${this.absoluteFile}"
                    val r3 = getExifOrientation()
                    "--> r3: $r3".e
                }
            } ?: throw IOException("Failed to get output stream.")
        } ?: throw IOException("Failed to create new MediaStore record.")
    }

    return null
}

fun Bitmap.saveBmpToFile(context: Context): File? {
    val imageStorageAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val filename = "shadertoy_${System.currentTimeMillis()}.jpg"
    val imageDetails = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.ORIENTATION, "90")
        put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis())
    }

    // Save the image.
    context.contentResolver.apply {
        insert(imageStorageAddress, imageDetails)?.let { uri ->
            openOutputStream(uri)?.use { outStream ->
                val isBitmapCompressed = compress(
                    Bitmap.CompressFormat.JPEG, 100, outStream
                )
//                File(getRealPath(context, uri)!!)
                recycle()

                val file = getRealPath(context, uri)?.let { File(it) }
                return file?.absoluteFile
            } ?: throw IOException("Failed to get output stream.")
        } ?: throw IOException("Failed to create new MediaStore record.")
    }

    return null
}

fun File.share(context: Context) {
    val uri = FileProvider.getUriForFile(
        context, BuildConfig.APPLICATION_ID + ".provider", this
    )
    ContextCompat.startActivity(context.applicationContext, Intent(Intent.ACTION_SEND).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setType(
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(this@share.extension)
        )
        putExtra(Intent.EXTRA_STREAM, uri)
        setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }, null)
}

fun File.play(context: Context) {
    val uri = FileProvider.getUriForFile(
        context, "${BuildConfig.APPLICATION_ID}.provider", this
    )
    // Launch external activity via intent to play video recorded using our provider
    ContextCompat.startActivity(context, Intent().apply {
        action = Intent.ACTION_VIEW
        type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(this@play.extension)
        data = uri
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }, null)
}

fun Context.createFile(extension: String): File {
    val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
    return File(this.filesDir, "shadertoy_${sdf.format(Date())}.$extension").apply {
        "--> Created output file: $absoluteFile".e
    }
}

fun File.saveVideoToDcim(context: Context): File? {
    val storageAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val filename = "shadertoy_${System.currentTimeMillis()}.${this@saveVideoToDcim.extension}"
    val details = ContentValues().apply {
        put(
            MediaStore.Video.Media.DISPLAY_NAME, filename
        )
        put(
            MediaStore.MediaColumns.MIME_TYPE, "video/mp4"
        )
        put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis())
    }

    context.contentResolver.apply {
        insert(storageAddress, details)?.let { uri ->
            openOutputStream(uri)?.use { outStream ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    this@saveVideoToDcim.inputStream().use {
                        FileUtils.copy(it, outStream)
                    }
                } else {
                    outStream.write(this@saveVideoToDcim.readBytes())
                }

//                this@saveToDcim.delete()

                return File(uri.path + "/" + filename).apply {
                    "--> Saved to DCIM ${this.absoluteFile}".e
                }
            } ?: throw IOException("Failed to get output stream.")
        } ?: throw IOException("Failed to create new MediaStore record.")
    }

    return null
}

val Context.cameraManager: CameraManager?
    get() = ContextCompat.getSystemService(this, CameraManager::class.java)
val Context.powerManager: PowerManager?
    get() = ContextCompat.getSystemService(this, PowerManager::class.java)
val Context.windowManager: WindowManager?
    get() = ContextCompat.getSystemService(this, WindowManager::class.java)

val Context.displayManager: DisplayManager?
    get() = ContextCompat.getSystemService(this, DisplayManager::class.java)

val Context.rotation: Int
    get() {
        return windowManager!!.defaultDisplay.rotation
//        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            display!!.rotation
//        } else {
//            windowManager!!.defaultDisplay.rotation
//        }
    }

val ORIENTATIONS: SparseIntArray = SparseIntArray(4).apply {
    append(Surface.ROTATION_0, 90)
    append(Surface.ROTATION_90, 0)
    append(Surface.ROTATION_180, 270)
    append(Surface.ROTATION_270, 180)
}

val Int.toOrientationTag: Int
    get() {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(this) + 90 + 270) % 360
    }

/**
 * That just doesn't work on my Xiaomi. Gallery ignores TAG_ORIENTATION
 */
fun File.setExifOrientation(orientation: Int) {
    // If the result is a JPEG file, update EXIF metadata with orientation info
    try {
        if (extension == "jpg") {
            ExifInterface(absolutePath).apply {
                setAttribute(
                    ExifInterface.TAG_ORIENTATION, "${orientation.toOrientationTag}"
                )
                saveAttributes()
            }
            "--> ${this.absoluteFile} TAG_ORIENTATION: ${getExifOrientation()}".e
        }
    } catch (ex: Exception) {
        "--> setExifOrientation ex: $ex".e
    }
}

fun File.getExifOrientation(): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return ExifInterface(absoluteFile).getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)
            .apply {
                "--> $absoluteFile has orientation: ${this@apply}".e
            }
    }

    return -1
}