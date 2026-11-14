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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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

    // Hide system bars while the camera is open, then restore them on exit.
    DisposableEffect(activity) {
        val window = activity?.window
        val insetsController = window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        insetsController?.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

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
    var layoutMode by remember { mutableStateOf(com.dualframe.camera.graphics.DualCompositionRenderer.Layout.PIP) }
    var pipSwapped by remember { mutableStateOf(false) }
    var pipX by remember { mutableStateOf(0.56f) }
    var pipY by remember { mutableStateOf(0.075f) }
    var pipSizeIndex by remember { mutableStateOf(1) }
    var zoom by remember { mutableStateOf(1f) }
    var showGrid by remember { mutableStateOf(true) }
    val pipWidths = listOf(0.32f, 0.40f, 0.48f)

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
                        0f to Color(0x66000000),
                        0.16f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color(0xCC000000)
                    )
                )
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current.density
                val pipWidth = maxWidth * pipWidths[pipSizeIndex]
                val pipHeight = pipWidth * (if (pipSwapped) 16f / 9f else 9f / 16f)
                if (layoutMode == com.dualframe.camera.graphics.DualCompositionRenderer.Layout.PIP) {
                    Box(
                        modifier = Modifier.offset(x = maxWidth * pipX, y = maxHeight * pipY)
                            .size(width = pipWidth, height = pipHeight)
                            .border(2.dp, Color(0xFFFACC15), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .pointerInput(maxWidth, maxHeight) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    pipX = (pipX + drag.x / (maxWidth.value * density)).coerceIn(0f, 1f - pipWidths[pipSizeIndex])
                                    pipY = (pipY + drag.y / (maxHeight.value * density)).coerceIn(0f, 0.78f)
                                }
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Row(Modifier.fillMaxWidth().background(Color(0xDD08090B)).padding(horizontal = 7.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (pipSwapped) "9:16 PORTRAIT" else "16:9 LANDSCAPE", color = Color(0xFFFACC15), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Row {
                                Text("↔", color = CameraWhite, modifier = Modifier.clickable { pipSwapped = !pipSwapped }.padding(horizontal = 6.dp))
                                Text("＋", color = CameraWhite, modifier = Modifier.clickable { pipSizeIndex = (pipSizeIndex + 1) % pipWidths.size }.padding(horizontal = 4.dp))
                            }
                        }
                    }
                }
                if (showGrid) {
                    Canvas(Modifier.fillMaxSize()) {
                        val thirdW = size.width / 3f
                        val thirdH = size.height / 3f
                        for (i in 1..2) {
                            drawLine(Color.White.copy(alpha = 0.22f), Offset(thirdW * i, 0f), Offset(thirdW * i, size.height), strokeWidth = 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, thirdH * i), Offset(size.width, thirdH * i), strokeWidth = 1.dp.toPx())
                        }
                    }
                }
                if (layoutMode == com.dualframe.camera.graphics.DualCompositionRenderer.Layout.STACKED) {
                    // A subtle border clarifies the two independent compositions.
                }
                /* stacked view content continues */
                if (layoutMode == com.dualframe.camera.graphics.DualCompositionRenderer.Layout.STACKED) {
                    Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).border(1.dp, Color(0xFFFACC15), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)), contentAlignment = Alignment.TopStart) { Text("16:9 LANDSCAPE", color = Color(0xFFFACC15), fontSize = 10.sp, modifier = Modifier.padding(8.dp)) }
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxHeight().aspectRatio(9f / 16f).border(1.dp, Color(0xFF34D399), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)), contentAlignment = Alignment.TopStart) { Text("9:16 PORTRAIT", color = Color(0xFF34D399), fontSize = 10.sp, modifier = Modifier.padding(8.dp)) }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 28.dp, top = 12.dp, end = 28.dp),
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

            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 82.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                listOf("PiP", "STACKED").forEachIndexed { index, label ->
                    val active = if (index == 0) layoutMode == com.dualframe.camera.graphics.DualCompositionRenderer.Layout.PIP else layoutMode == com.dualframe.camera.graphics.DualCompositionRenderer.Layout.STACKED
                    Text(label, color = if (active) Color.Black else CameraWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(if (active) Color(0xFFFACC15) else Color(0x99202020)).clickable { layoutMode = if (index == 0) com.dualframe.camera.graphics.DualCompositionRenderer.Layout.PIP else com.dualframe.camera.graphics.DualCompositionRenderer.Layout.STACKED }.padding(horizontal = 13.dp, vertical = 8.dp))
                }
                Text("SWAP", color = Color(0xFFFACC15), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { pipSwapped = !pipSwapped }.padding(horizontal = 10.dp, vertical = 8.dp))
                Text("GRID", color = if (showGrid) Color(0xFFFACC15) else CameraWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showGrid = !showGrid }.padding(horizontal = 6.dp, vertical = 8.dp))
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 25.dp, vertical = 122.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                listOf(0.6f, 1f, 2f, 5f).forEach { factor -> Text("${factor}x", color = if (zoom == factor) Color(0xFFFACC15) else CameraWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { zoom = factor }.padding(horizontal = 9.dp, vertical = 5.dp)) }
            }
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                        .background(Color(0x55222222)).border(1.dp, Color(0x99FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▧", color = CameraWhite, fontSize = 28.sp)
                }

                Box(
                    modifier = Modifier.size(88.dp).clip(CircleShape)
                        .border(3.dp, CameraWhite, CircleShape).padding(6.dp)
                        .border(2.dp, CameraWhite, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(CameraWhite))
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { isVideoMode = !isVideoMode }
                ) {
                    Text("▣", color = CameraWhite, fontSize = 28.sp)
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
    LaunchedEffect(layoutMode, pipSwapped) { glView.setLayout(layoutMode, pipSwapped) }
    LaunchedEffect(pipX, pipY) { glView.setPipPosition(pipX, pipY) }
    LaunchedEffect(pipSizeIndex) { glView.setPipWidth(pipWidths[pipSizeIndex]) }
    LaunchedEffect(zoom) { glView.setZoom(zoom) }
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
