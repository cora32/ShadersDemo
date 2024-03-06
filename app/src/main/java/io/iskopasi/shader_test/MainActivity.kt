package io.iskopasi.shader_test

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Picture
import android.graphics.RenderEffect
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
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


private val Picture.toBitmap: Bitmap
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

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val controller = DrawerController()

    // Font loader debugger
    private val handler = CoroutineExceptionHandler { _, throwable ->
        Log.e("shader_test", "There has been an issue: ", throwable)
    }
    private lateinit var drawerState: DrawerState
    private lateinit var scope: CoroutineScope

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onShaderClicked(shader: Shaders) {
        scope.launch {
            controller.onShaderClick(shader)
            drawerState.close()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
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
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
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
                Item(Shaders.TestShader)
            }
        }
    }

    @Composable
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
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

    @Composable
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun ImageHolder(shader: Shaders) {
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
    fun MiniShaderCanvas(shader: Shaders) {
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
                                androidx.compose.ui.graphics
                                    .Canvas(
                                        picture
                                            .beginRecording(
                                                width,
                                                height
                                            )
                                    )
                            val pictureCanvas2 =
                                androidx.compose.ui.graphics
                                    .Canvas(
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

    @Preview(showBackground = true)
    @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
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
}