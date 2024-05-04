package io.iskopasi.shader_test.ui.composables

import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.iskopasi.shader_test.DrawerController
import io.iskopasi.shader_test.R
import io.iskopasi.shader_test.ui.theme.Shader_testTheme
import io.iskopasi.shader_test.ui.theme.colorOrange
import io.iskopasi.shader_test.ui.theme.colorSwitchTrackBg
import io.iskopasi.shader_test.ui.theme.colorTextWhite
import io.iskopasi.shader_test.utils.EmptyShader
import io.iskopasi.shader_test.utils.Shaders
import io.iskopasi.shader_test.utils.applyShader
import io.iskopasi.shader_test.utils.screenshot
import io.iskopasi.shader_test.utils.toPx


@Composable
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun ImageHolder(
    shader: Shaders,
    width: Dp,
    height: Dp,
    controller: DrawerController = viewModel()
) {
    val modifier = if (shader.shaderHolder is EmptyShader) Modifier
        .blur(4.dp)
    else {
        shader.shaderHolder.setParams(
            width = 40f.dp.toPx(),
            height = height.toPx(),
        )

        Modifier.applyShader(shader)
    }

    if (controller.bitmap.value.width > 0) {
        Image(
            bitmap = controller.bitmap.value.asImageBitmap(),
//            contentScale = FixedScale(1f),
            contentDescription = null,
            modifier = modifier
                .background(Color.Green)
                .wrapContentSize(align = Alignment.BottomCenter, unbounded = true)
                .graphicsLayer {
                    translationX = -(width / 2f).toPx()
                }

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

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
    ) {
        Canvas(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .screenshot(controller.bitmap)
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

        BoxWithConstraints(
            modifier = Modifier
                .width(40.dp)
                .height(80.dp)
                .align(Alignment.CenterEnd)
        ) {
            ImageHolder(shader, maxWidth, maxHeight)
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
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null
            )
        }
    }
}

@Composable
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun LeftSide(onClick: (Shaders) -> Unit, onCameraSwitchChanged: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .padding(top = 24.dp, bottom = 55.dp)
                .fillMaxHeight(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 80.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                items(Shaders.entries) {
                    Item(it, onClick)
                }
            }
            CameraSwitch(onCameraSwitchChanged)
        }
    }
}

@Composable
fun CameraSwitch(onCameraSwitchChanged: (Boolean) -> Unit) {
    val checked = remember {
        mutableStateOf(false)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(
                    R.string.camera_state,
                    stringResource(if (checked.value) R.string.on else R.string.off)
                ),
                color = if (checked.value) colorOrange else colorTextWhite
            )
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = checked.value,
                onCheckedChange = { enabled ->
                    checked.value = enabled
                    onCameraSwitchChanged(enabled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorOrange,
                    checkedTrackColor = colorSwitchTrackBg,
                    checkedBorderColor = colorTextWhite,
//                            uncheckedThumbColor = Color.Yellow,
//                            uncheckedTrackColor = Color.Blue,
                ),
            )
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun LeftSidePreview() {
    Shader_testTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            LeftSide({}, { })
        }
    }
}