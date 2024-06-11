package io.iskopasi.shader_test

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.createFontFamilyResolver
import io.iskopasi.shader_test.ui.composables.CameraView
import io.iskopasi.shader_test.ui.theme.Shader_testTheme
import io.iskopasi.shader_test.utils.Shaders
import io.iskopasi.shader_test.utils.checkPermissions
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    private val controller: DrawerController by viewModels()

    private val cameraPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultMap ->
            if (resultMap.values.all { it }) {
                // Implement camera related  code
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onShaderClicked(shader: Shaders) {
        scope.launch {
            controller.onShaderClick(shader)
            drawerState.close()
        }
    }

    private fun onCameraSwitchChanged(enabled: Boolean) {
        if (checkPermissions(this)) {
            controller.onCameraViewSwitch(enabled)
            scope.launch {
                drawerState.close()
            }
        } else {
            cameraPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock screen brightness
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Prevents screen rotation
//        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))

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
                        CameraView(controller)
//                        ModalNavigationDrawer(
//                            drawerState = drawerState,
//                            drawerContent = {
//                                LeftSide(
//                                    ::onShaderClicked,
//                                    ::onCameraSwitchChanged
//                                )
//                            }) {
//                            Box(modifier = Modifier
//                                .pointerInput(Unit) {
//                                    detectDragGestures { change, dragAmount ->
//                                        change.consume()
//                                    }
//                                }) { RightSide() }
//                        }
                    }
                }
            }
        }
    }
}