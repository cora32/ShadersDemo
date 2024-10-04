package io.iskopasi.shader_test

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.dp
import io.iskopasi.shader_test.ui.composables.LeftSide
import io.iskopasi.shader_test.ui.composables.RightSide
import io.iskopasi.shader_test.ui.theme.Shader_testTheme
import io.iskopasi.shader_test.utils.Shaders
import io.iskopasi.shader_test.utils.checkPermissions
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    private val controller: DrawerController by viewModels()
//    private val galleryModel: GalleryModel by viewModels()

    private val cameraPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultMap ->
            if (resultMap.values.all { it }) {
                openCamera(true)
            } else {
                Toast.makeText(this, "We need your permission", Toast.LENGTH_LONG)
            }
        }

    // Font loader debugger
    private val handler = CoroutineExceptionHandler { _, throwable ->
        Log.e("shader_test", "There has been an issue: ", throwable)
    }
    private lateinit var drawerState: DrawerState
    private lateinit var scope: CoroutineScope

    private fun onShaderClicked(shader: Shaders) {
        scope.launch {
            controller.onShaderClick(shader)
            delay(200L)
            drawerState.close()
        }
    }

    private fun onCameraSwitchChanged(enabled: Boolean) {
        if (checkPermissions(this)) {
            openCamera(enabled)
        } else {
            cameraPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
//                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun openCamera(enabled: Boolean) {
        controller.onCameraViewSwitch(enabled)
        scope.launch {
            delay(300L)
            drawerState.close()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock screen brightness
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Prevents screen rotation
//        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

//        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

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
//                        CameraView(controller)
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                ModalDrawerSheet(
                                    drawerState,
                                    modifier = Modifier.width(300.dp)
                                ) {
                                    LeftSide(
                                        ::onShaderClicked,
                                        ::onCameraSwitchChanged
                                    )
                                }
                            },
                        ) {
                            Box(
                                modifier = Modifier.padding(top = 16.dp)
//                                .pointerInput(Unit) {
//                                    detectDragGestures { change, dragAmount ->
//                                        change.consume()
//                                    }
//                                }
                            ) {
                                RightSide()
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            drawerState.open()
                                        }
                                    },

                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 32.dp, end = 16.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Menu,
                                        contentDescription = stringResource(R.string.menu),
                                        tint = Color.White,
                                        modifier =
                                        Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

