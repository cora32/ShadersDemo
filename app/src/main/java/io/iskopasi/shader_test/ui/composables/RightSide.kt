package io.iskopasi.shader_test.ui.composables

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.iskopasi.shader_test.DrawerController
import io.iskopasi.shader_test.R
import io.iskopasi.shader_test.utils.Shaders


@Composable
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun RightSide(controller: DrawerController = viewModel()) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxSize()
    ) {
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
                contentAlignment = Alignment.CenterStart
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
                    ShaderCanvas(controller.currentShader.value)
                }
            }
        }
    }
}

@Composable
fun ShaderCanvas(shader: Shaders) {
    val blend = BlendMode.Difference
    val image = ImageBitmap.imageResource(id = R.drawable.img)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
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