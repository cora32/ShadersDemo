package io.iskopasi.shader_test.ui.composables

import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.iskopasi.shader_test.DrawerController
import io.iskopasi.shader_test.R
import io.iskopasi.shader_test.ui.theme.Shader_testTheme
import io.iskopasi.shader_test.utils.EmptyShader
import io.iskopasi.shader_test.utils.applyShader
import io.iskopasi.shader_test.utils.screenshot
import io.iskopasi.shader_test.utils.toPx


@Composable
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun RightSide(controller: DrawerController = viewModel()) {
    if (controller.cameraEnabled.value) {
        CameraView(controller)
    } else {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .padding(start = 40.dp, end = 16.dp)
                .fillMaxSize()
        ) {
            DemoView(controller)
        }
    }
}

@Composable
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun DemoView(controller: DrawerController) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            controller.currentShader.value.shaderName,
            modifier = Modifier.padding(top = 32.dp),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        .5.dp,
                        MaterialTheme.colorScheme.secondary,
                        RoundedCornerShape(16.dp)
                    )
                    .height(500.dp),
            ) {
                PictureView(controller)
            }
            ShaderViewport(controller)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun ShaderViewport(controller: DrawerController) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var bottom = 0f
    var left = 0f
    var right = 0f
    val circleWidth = 150f
    val leftPadding = circleWidth / 2f
    val height = 500.dp
    var top = 0f

    Log.e("->>", "-->> Composing ShaderViewport");

    val bitmap = controller.bitmapBig.value.asImageBitmap()
    val shader = controller.currentShader.value
    val modifier = if (shader.shaderHolder is EmptyShader) Modifier
        .blur(4.dp)
    else {
        shader.shaderHolder.setParams(
            width = circleWidth.dp.toPx(),
            height = circleWidth.dp.toPx(),
        )

        Modifier.applyShader(shader)
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = offsetX
                translationY = offsetY
            }
            .onGloballyPositioned {
                if (left == 0f) {
                    left = -it.positionInParent().x
                    right = it.positionInParent().x - circleWidth / 2f + leftPadding
                    top = -(it.positionInParent().y - height.value / 2f)
                    bottom = it.positionInParent().y - height.value / 2f
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()

                    if (offsetX in left..right) {
                        offsetX += dragAmount.x

                        if (offsetX < left) {
                            offsetX = left
                        } else if (offsetX > right) {
                            offsetX = right
                        }
                    }

                    if (offsetY in top..bottom) {
                        offsetY += dragAmount.y

                        if (offsetY < top) {
                            offsetY = top
                        } else if (offsetY > bottom) {
                            offsetY = bottom
                        }
                    }
                }
            }
//            .offset{IntOffset(offsetX.roundToInt(), offsetY.roundToInt())}
//            .layout { measurable, constraints ->
//                val placeable = measurable.measure(constraints)
//                layout(placeable.width, placeable.height) {
////                    placeable.placeRelative(offsetX.toInt(), offsetY.toInt())
////                    placeable.place(IntOffset(offsetX.roundToInt(), offsetY.roundToInt()))
//                }
//            }
            .clip(CircleShape)
            .border(
                1.dp,
                Color.White.copy(alpha = 0.5f),
                CircleShape
            )
    ) {
        Image(
            bitmap = bitmap,
//            contentScale = FixedScale(1f),
            contentDescription = null,
            modifier = modifier
                .size(circleWidth.dp)
                .background(Color.Green)
                .wrapContentSize(align = Alignment.Center, unbounded = true)
                .graphicsLayer {
                    translationX = -offsetX
                    translationY = -offsetY
                }

        )
    }

}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun PictureView(controller: DrawerController) {
    val blend = BlendMode.Difference
    val image = ImageBitmap.imageResource(id = R.drawable.img)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .screenshot(controller.bitmapBig)
    ) {
        val checkWidth = size.width / 20
        val checkHeight = size.height / 20

        for (i in 0..19) {
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

        drawImage(
            image,
            topLeft = Offset(
                size.width / 2f - image.width / 2f,
                size.height / 2f - image.height / 2f,
            )
        )
    }
}


@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun RightSidePreview() {
    Shader_testTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            RightSide()
        }
    }
}