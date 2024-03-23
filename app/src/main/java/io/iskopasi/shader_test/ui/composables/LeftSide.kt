package io.iskopasi.shader_test.ui.composables

import android.graphics.Picture
import android.graphics.RenderEffect
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.iskopasi.shader_test.DrawerController
import io.iskopasi.shader_test.utils.NativeBlurShaderHolder
import io.iskopasi.shader_test.utils.Shaders
import io.iskopasi.shader_test.utils.toBitmap


@Composable
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun ImageHolder(
    shader: Shaders,
    controller: DrawerController = viewModel()
) {
    shader.shaderHolder.setParams(
        mapOf(
            "width" to 40f,
            "height" to 80f,
        )
    )

    val modifier = if (shader.shaderHolder is NativeBlurShaderHolder) Modifier
        .fillMaxSize()
        .blur(4.dp)
    else
        Modifier
            .fillMaxSize()
            .graphicsLayer(
                clip = true,
                renderEffect = RenderEffect
                    .createRuntimeShaderEffect(
                        shader.shaderHolder.shader!!,
                        "inputShader"
                    )
                    .asComposeRenderEffect()
//                    renderEffect = RenderEffect.createBlurEffect(8f,8f, Shader.TileMode.MIRROR).asComposeRenderEffect()
            )

    if (controller.picture.value.width > 0) {
        Image(
            bitmap = controller.picture.value.toBitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
        )
    } else Box {}
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun MiniShaderCanvas(
    shader: Shaders,
    controller: DrawerController = viewModel()
) {
    val blend = BlendMode.Difference
    val sSize = 100f
    val picture = Picture()
    val picture2 = Picture()

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
    ) {
        Canvas(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .drawWithCache {
                    val width = this.size.width.toInt()
                    val height = this.size.height.toInt()
                    onDrawWithContent {
                        val pictureCanvas =
                            Canvas(
                                picture
                                    .beginRecording(
                                        width,
                                        height
                                    )
                            )
                        val pictureCanvas2 =
                            Canvas(
                                picture2
                                    .beginRecording(
                                        width / 2,
                                        height
                                    )
                                    .apply { translate(-width / 2f, 0f) }
                            )

                        draw(this, this.layoutDirection, pictureCanvas, size) {
                            this@onDrawWithContent.drawContent()
                        }
                        draw(this, this.layoutDirection, pictureCanvas2, size) {
                            this@onDrawWithContent.drawContent()
                        }
                        picture.endRecording()
                        picture2.endRecording()

                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawPicture(picture)

                            controller.picture.value = picture2
                        }
                    }
                }
        ) {
            val checkWidth = size.width / 5
            val checkHeight = size.height / 5

            for (i in 0..5) {
                // Draw vertical lines
                drawRect(
                    if (i % 2 == 0) Color.Black else Color.White,
                    topLeft = Offset(i * checkWidth, 0f),
                    size = Size(checkWidth, size.height),
                    blendMode = blend
                )
                // Draw horizontal lines
                drawRect(
                    if (i % 2 == 0) Color.White else Color.Black,
                    topLeft = Offset(0f, i * checkHeight),
                    size = Size(size.width, checkHeight),
                    blendMode = blend
                )
            }

            drawRect(
                Color(0xFFFF9C09),
                topLeft = Offset(size.width / 2f - sSize / 2f, size.height / 2f - sSize / 2f),
                size = Size(sSize, sSize),
            )
        }

        Box(
            modifier = Modifier
                .width(40.dp)
                .align(Alignment.CenterEnd)
        ) {
            Log.e("->>>", "drawing ImageHolder")
            ImageHolder(shader)
        }
    }
}

@Composable
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Item(
    shader: Shaders,
    onShaderClicked: (Shaders) -> Unit,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    controller: DrawerController = viewModel()
) {
    Surface(
        modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(.5.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp)),

        onClick = { onShaderClicked(shader) },
        tonalElevation = if (controller.currentShader.value == shader) 5.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniShaderCanvas(shader = shader)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = shader.shaderName,
                modifier = Modifier.padding(all = 4.dp),
                style = style,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.KeyboardArrowRight,
                contentDescription = null
            )
        }
    }
}

@Composable
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun LeftSide(onClick: (Shaders) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .fillMaxHeight(),

            ) {
            Spacer(modifier = Modifier.height(80.dp))
            Item(Shaders.BoxBlurShader, onClick)
            Item(Shaders.GoogleBlurShader, onClick)
            Item(Shaders.TestShader, onClick)
        }
    }
}