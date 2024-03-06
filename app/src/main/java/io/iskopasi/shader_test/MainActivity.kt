package io.iskopasi.shader_test

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.iskopasi.shader_test.ui.theme.Shader_testTheme
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    private val controller = DrawerController()

    // Font loader debugger
    private val handler = CoroutineExceptionHandler { _, throwable ->
        Log.e("shader_test", "There has been an issue: ", throwable)
    }
    private lateinit var drawerState: DrawerState
    private lateinit var scope: CoroutineScope

    private fun onShaderClicked(shader: Shaders) {
        scope.launch {
            controller.onShaderClick(shader)
            drawerState.close()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            scope = rememberCoroutineScope()

            Shader_testTheme {
                // A surface container using the 'background' color from the theme
                CompositionLocalProvider(
                    LocalFontFamilyResolver provides createFontFamilyResolver(
                        LocalContext.current,
                        handler
                    )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = { LeftSide() }) {
                            RightSide()
                        }
                    }
                }
            }
        }
    }


    @Composable
    fun Item(shader: Shaders, style: TextStyle = MaterialTheme.typography.labelMedium) {
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
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.Yellow)
                )
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
    fun LeftSide() {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .fillMaxHeight(),

                ) {
                Spacer(modifier = Modifier.height(80.dp))
                Item(Shaders.BoxBlurShader)
                Item(Shaders.GoogleBlurShader)
            }
        }
    }

    @Composable
    fun RightSide() {
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
                            ),
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
                .fillMaxWidth()
                .height(500.dp)
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

    @Preview(showBackground = true)
    @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun LeftSidePreview() {
        Shader_testTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                LeftSide()
            }
        }
    }

    @Preview(showBackground = true)
    @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
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
}