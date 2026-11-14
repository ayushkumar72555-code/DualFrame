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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
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

private val CameraWhite = Color(0xFFF7F7F7)
private val CameraBlack = Color(0xFF050505)

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
    var showSettings by remember { mutableStateOf(false) }
    var isVideoMode by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
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

    Box(modifier = Modifier.fillMaxSize().background(CameraBlack)) {
        if (hasCameraPermission) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { glView })
            // A subtle lower gradient keeps the camera controls readable.
            Box(
                modifier = Modifier.fillMaxSize().background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color(0x55000000),
                        0.16f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color(0xB8000000)
                    )
                )
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val frameWidth = maxWidth * 0.89f
                val frameHeight = frameWidth * 9f / 16f
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * 0.055f, y = maxHeight * 0.07f)
                        .size(width = frameWidth, height = frameHeight)
                        .border(2.dp, CameraBlack, androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                )
                Text(
                    "16:9",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-maxWidth * 0.10f), y = maxHeight * 0.072f),
                    color = CameraBlack,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * 0.28f, y = maxHeight * 0.20f)
                        .size(width = maxWidth * 0.44f, height = maxHeight * 0.49f)
                        .border(2.dp, CameraBlack, androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                )
                Text(
                    "9:16",
                    modifier = Modifier
                        .offset(x = maxWidth * 0.63f, y = maxHeight * 0.205f),
                    color = CameraBlack,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CameraTopIcon("⚙", "Settings") { showSettings = true }
                Text(
                    if (isVideoMode) "Video" else "Photo",
                    color = CameraWhite,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )
                CameraTopIcon("↻", "Switch camera") {
                    alternateCamera?.let {
                        selectedCameraId = it.id
                        previewStarted = false
                        previewError = null
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = 28.dp, end = 28.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(66.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                        .background(Color(0x55222222)).border(1.dp, Color(0x99FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▧", color = CameraWhite, fontSize = 28.sp)
                }

                Box(
                    modifier = Modifier.size(104.dp).clip(CircleShape)
                        .border(3.dp, CameraWhite, CircleShape).padding(7.dp)
                        .border(2.dp, CameraWhite, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(54.dp).clip(CircleShape).background(CameraWhite))
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { isVideoMode = !isVideoMode }
                ) {
                    Text("▣", color = CameraWhite, fontSize = 34.sp)
                    Text(if (isVideoMode) "PHOTO" else "VIDEO", color = CameraWhite, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (!previewStarted) {
                Text(
                    previewError ?: discoveryError ?: "Waiting for camera…",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp),
                    color = CameraWhite,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (showSettings) {
                AlertDialog(
                    onDismissRequest = { showSettings = false },
                    title = { Text("Camera settings") },
                    text = {
                        Text(
                            "DualFrame preview\nLandscape: 16:9\nPortrait: 9:16\nCamera: ${selectedCamera?.displayName ?: "Not selected"}\n\nCapture and recording controls will be connected in a later milestone."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showSettings = false }) { Text("Done") }
                    }
                )
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera access needed", color = CameraWhite, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    "DualFrame needs camera access to show both compositions from one live camera feed.",
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = {
                    hasRequestedPermission = true
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) { Text("Grant camera permission", color = CameraWhite) }
                if (shouldOfferSettings) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    }) { Text("Open app settings", color = CameraWhite) }
                }
                TextButton(onClick = onBack) { Text("Back", color = CameraWhite) }
            }
        }
    }

    LaunchedEffect(selectedCamera) {
        selectedCamera?.let(controller::selectCamera)
    }
}

@Composable
private fun CameraTopIcon(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = CameraWhite, fontSize = 31.sp, textAlign = TextAlign.Center)
    }
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
