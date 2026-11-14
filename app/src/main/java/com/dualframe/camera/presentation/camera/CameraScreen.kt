package com.dualframe.camera.presentation.camera

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dualframe.camera.camera.Camera2PreviewController
import com.dualframe.camera.camera.data.CameraCatalog
import com.dualframe.camera.camera.model.CameraDescriptor
import com.dualframe.camera.graphics.DualCompositionGLSurfaceView

@Composable
fun CameraScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasRequestedPermission by remember { mutableStateOf(false) }
    var cameras by remember { mutableStateOf<List<CameraDescriptor>>(emptyList()) }
    var selectedCameraId by remember { mutableStateOf<String?>(null) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var previewStarted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) previewStarted = false
    }

    val controller = remember(context) {
        Camera2PreviewController(context).apply {
            onPreviewError = { message ->
                previewError = message
                previewStarted = false
            }
            onPreviewStarted = {
                previewError = null
                previewStarted = true
            }
        }
    }

    val glView = remember(context, controller) {
        DualCompositionGLSurfaceView(context).apply {
            onInputSurfaceChanged = { surfaceTexture ->
                if (surfaceTexture == null) controller.detachInputSurface()
                else controller.attachInputSurface(surfaceTexture)
            }
        }
    }

    DisposableEffect(lifecycleOwner, controller, glView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    glView.onResume()
                    controller.start()
                    hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                }
                Lifecycle.Event.ON_PAUSE -> {
                    controller.stop()
                    glView.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            glView.onResume()
            controller.start()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
            glView.releaseCameraInput()
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            try {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                cameras = CameraCatalog(manager).discover()
                discoveryError = if (cameras.isEmpty()) "No camera was detected on this device." else null
                if (selectedCameraId == null || cameras.none { it.id == selectedCameraId }) {
                    selectedCameraId = cameras.firstOrNull()?.id
                }
            } catch (exception: Exception) {
                cameras = emptyList()
                discoveryError = exception.message ?: "Unable to inspect the available cameras."
            }
        } else {
            cameras = emptyList()
            selectedCameraId = null
        }
    }

    val selectedCamera = cameras.firstOrNull { it.id == selectedCameraId }
    val alternateCamera = selectedCamera?.let { selected ->
        cameras.firstOrNull { it.id != selected.id && it.lensFacing != selected.lensFacing }
    }
    val showRationale = activity?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
    } ?: false
    val shouldOfferSettings = hasRequestedPermission && !hasCameraPermission && !showRationale

    Scaffold(containerColor = Color(0xFF101010)) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("BACK") }
                Text("DUALFRAME CAMERA", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }

            if (!hasCameraPermission) {
                Spacer(Modifier.height(32.dp))
                Text("Camera access needed", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(10.dp))
                Text(
                    "DualFrame needs camera access to render two live compositions from one camera stream.",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = {
                    hasRequestedPermission = true
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) { Text("Grant camera permission") }
                if (shouldOfferSettings) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    }) { Text("Open app settings") }
                }
                Spacer(Modifier.weight(1f))
            } else {
                Text(
                    selectedCamera?.displayName ?: "Camera discovery",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                val cameraInfo = selectedCamera?.let {
                    "Hardware: " + it.hardwareLevel + "  •  Flash: " +
                        if (it.flashAvailable) "Available" else "Unavailable"
                } ?: (discoveryError ?: "Inspecting camera hardware...")
                Text(cameraInfo, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Color.Black)
                ) {
                    AndroidView(modifier = Modifier.fillMaxSize(), factory = { glView })

                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "PORTRAIT  •  9:16",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                        Text(
                            if (previewStarted) "ONE CAMERA STREAM  •  TWO COMPOSITIONS" else (previewError ?: "Waiting for camera input..."),
                            color = if (previewStarted) MaterialTheme.colorScheme.primary else Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.CenterHorizontally).background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                        Text(
                            "LANDSCAPE  •  16:9",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (previewStarted) "DUAL PREVIEW ACTIVE" else "PREVIEW NOT RUNNING",
                        color = if (previewStarted) MaterialTheme.colorScheme.primary else Color.LightGray,
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (alternateCamera != null) {
                        Button(onClick = {
                            selectedCameraId = alternateCamera.id
                            previewStarted = false
                            previewError = null
                        }) { Text("Switch camera") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Live OpenGL preview only • Photo and video capture are not implemented yet",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    LaunchedEffect(selectedCamera) {
        selectedCamera?.let(controller::selectCamera)
    }
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
