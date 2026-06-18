package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Global file upload references for administration forms in WebView
    private var mUploadCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                Array(count) { i -> data.clipData!!.getItemAt(i).uri }
            } else if (data?.data != null) {
                arrayOf(data.data!!)
            } else {
                null
            }
        } else {
            null
        }
        mUploadCallback?.onReceiveValue(uris)
        mUploadCallback = null
    }

    // Permission and connection request launchers
    private val permissionsState = mutableStateOf(false)

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionsState.value = hasRequiredPermissions(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        permissionsState.value = hasRequiredPermissions(this)

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) { innerPadding ->
                    AppNavigation(
                        modifier = Modifier.padding(innerPadding),
                        hasPermissions = permissionsState.value,
                        onRequestPermissions = {
                            requestPermissionsLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                            startActivity(intent)
                        },
                        fileChooserLauncher = fileChooserLauncher,
                        onSetUploadCallback = { mUploadCallback = it }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reactively check permissions in case the user enabled them via system settings
        permissionsState.value = hasRequiredPermissions(this)
    }

    private fun hasRequiredPermissions(context: Context): Boolean {
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val fineLocationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return cameraGranted && (fineLocationGranted || coarseLocationGranted)
    }
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    fileChooserLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onSetUploadCallback: (ValueCallback<Array<Uri>>?) -> Unit
) {
    val context = LocalContext.current
    var isOnline by remember { mutableStateOf<Boolean?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var isCheckingNetwork by remember { mutableStateOf(false) }

    // Check internet connection
    LaunchedEffect(hasPermissions, retryCount) {
        if (hasPermissions) {
            isCheckingNetwork = true
            delay(400) // Aesthetic delay for seamless UI states
            isOnline = isInternetAvailable(context)
            isCheckingNetwork = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!hasPermissions) {
            PermissionRequiredScreen(
                onRequestPermissions = onRequestPermissions,
                onOpenSettings = onOpenSettings
            )
        } else {
            when {
                isCheckingNetwork || isOnline == null -> {
                    CheckingConnectionScreen()
                }
                isOnline == false -> {
                    NoInternetScreen(
                        onRetry = {
                            retryCount++
                        }
                    )
                }
                else -> {
                    // All permissions are active, and internet is verified! Display the fullscreen WebView
                    WebViewContainer(
                        url = "https://sipegawaismkpp.web.app/",
                        fileChooserLauncher = fileChooserLauncher,
                        onSetUploadCallback = onSetUploadCallback,
                        onConnectionLost = {
                            isOnline = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequiredScreen(
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val isWifiActive = remember { isInternetAvailable(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    )
                )
            )
            .padding(24.dp)
            .testTag("permissions_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Modern brand emblem/icon for SMKPP Negeri Bima (TU & Kepegawaian)
                BrandLogoSMKPPN(
                    size = 84.dp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Pengecekan Sistem",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Aplikasi memerlukan izin berikut untuk melanjutkan akses ke Sistem Kepegawaian:",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Checklist Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Item 1: Location Permission
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Lokasi",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Izin Lokasi Presensi",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        // Verified State Checkmark/Badge
                        if (hasLocation) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Aktif",
                                tint = Color(0xFF15803D),
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color(0xFFDCFCE7), CircleShape)
                                    .padding(4.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(18.dp)
                                    .background(Color(0xFFE0E0E0), RoundedCornerShape(9.dp)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .size(14.dp)
                                        .background(Color(0xFF938F99), CircleShape)
                                )
                            }
                        }
                    }

                    // Item 2: Camera Permission
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Kamera",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Izin Kamera (Scan QR)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        // Verified State Checkmark/Badge
                        if (hasCamera) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Aktif",
                                tint = Color(0xFF15803D),
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color(0xFFDCFCE7), CircleShape)
                                    .padding(4.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(18.dp)
                                    .background(Color(0xFFE0E0E0), RoundedCornerShape(9.dp)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .size(14.dp)
                                        .background(Color(0xFF938F99), CircleShape)
                                )
                            }
                        }
                    }

                    // Item 3: Connection Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Jaringan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Koneksi Internet",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (isWifiActive) "AKTIF" else "NO JARINGAN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isWifiActive) Color(0xFF15803D) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .testTag("settings_button")
                            .height(44.dp)
                    ) {
                        Text(
                            text = "PENGATURAN",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier
                            .testTag("request_permissions_button")
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp)
                    ) {
                        Text(
                            text = "AKTIFKAN",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CheckingConnectionScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandLogoSMKPPN(
            size = 110.dp,
            modifier = Modifier.padding(bottom = 28.dp)
        )

        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Memeriksa jaringan internet...",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Menghubungkan ke portal TU SMKPPN BIMA",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun NoInternetScreen(
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .testTag("no_internet_screen"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandLogoSMKPPN(
            size = 90.dp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SignalWifiOff,
                contentDescription = "Internet Mati",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Koneksi Terputus",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Pastikan perangkat Anda terhubung dengan koneksi internet aktif (Paket Data / Wi-Fi) untuk dapat membuka portal TU SMKPPN BIMA.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(48.dp)
                .testTag("retry_connection_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Coba Lagi",
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Coba Lagi",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun WebViewContainer(
    url: String,
    fileChooserLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onSetUploadCallback: (ValueCallback<Array<Uri>>?) -> Unit,
    onConnectionLost: () -> Unit
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var scaleProgress by remember { mutableIntStateOf(0) }
    var hasLoadError by remember { mutableStateOf(false) }

    // State to verify if the back button can navigate inside standard state
    var canGoBackState by remember { mutableStateOf(false) }

    // Intercept hardware/gesture back press to traverse WebView history before app exit
    BackHandler(enabled = canGoBackState) {
        webViewInstance?.goBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- Sleek Interface Premium Navigation Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { webViewInstance?.goBack() },
                enabled = canGoBackState,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (canGoBackState) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Kembali",
                    tint = if (canGoBackState) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "TU SMKPPN BIMA",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    maxLines = 1
                )
                Text(
                    text = "sipegawaismkpp.web.app",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    ),
                    maxLines = 1
                )
            }

            IconButton(
                onClick = { webViewInstance?.reload() },
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Muat Ulang Portal",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Animated gradient/linear line representing continuous load
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { scaleProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (hasLoadError) {
                NoInternetScreen(
                    onRetry = {
                        hasLoadError = false
                        webViewInstance?.reload()
                    }
                )
            } else {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("webview"),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            // Optimize settings for robust dynamic HTML and camera integrations
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            settings.setGeolocationEnabled(true)
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.mediaPlaybackRequiresUserGesture = false

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    canGoBackState = view?.canGoBack() == true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    canGoBackState = view?.canGoBack() == true
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        hasLoadError = true
                                        onConnectionLost()
                                    }
                                    canGoBackState = view?.canGoBack() == true
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    scaleProgress = newProgress
                                    canGoBackState = view?.canGoBack() == true
                                }

                                override fun onGeolocationPermissionsShowPrompt(
                                    origin: String?,
                                    callback: GeolocationPermissions.Callback?
                                ) {
                                    callback?.invoke(origin, true, false)
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    onSetUploadCallback(filePathCallback)
                                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "*/*"
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                    }
                                    try {
                                        fileChooserLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        onSetUploadCallback(null)
                                        return false
                                    }
                                    return true
                                }
                            }

                            loadUrl(url)
                            webViewInstance = this
                        }
                    },
                    update = {
                        it.canGoBack().let { canBack ->
                            if (canGoBackState != canBack) {
                                canGoBackState = canBack
                            }
                        }
                    }
                )

                // Indeterminate full overlay initially loading to look modern
                if (isLoading && scaleProgress < 30) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(50.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Menyelaraskan Sistem Kepegawaian...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mohon tunggu sebentar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun isInternetAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
fun BrandLogoSMKPPN(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // High fidelity custom agricultural crest base
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Subtle glowing sun background
            drawCircle(
                color = Color(0x22FFD54F), // Amber/Golden sun representing rich agricultural growth
                radius = w * 0.42f,
                center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.5f)
            )

            // 2. Custom elegant shield outline path
            val crestPath = Path().apply {
                moveTo(w * 0.5f, h * 0.12f)
                lineTo(w * 0.82f, h * 0.26f)
                lineTo(w * 0.82f, h * 0.64f)
                quadraticTo(w * 0.82f, h * 0.84f, w * 0.5f, h * 0.92f)
                quadraticTo(w * 0.18f, h * 0.84f, w * 0.18f, h * 0.64f)
                lineTo(w * 0.18f, h * 0.26f)
                close()
            }

            drawPath(
                path = crestPath,
                color = Color(0x11FFFFFF),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )

            drawPath(
                path = crestPath,
                color = Color.White.copy(alpha = 0.75f),
                style = Stroke(width = 2.dp.toPx())
            )

            // 3. Decorative agricultural vector elements - modern minimal geometric leaves
            // Left leaf
            val leftLeaf = Path().apply {
                moveTo(w * 0.5f, h * 0.55f)
                quadraticTo(w * 0.32f, h * 0.45f, w * 0.35f, h * 0.32f)
                quadraticTo(w * 0.48f, h * 0.34f, w * 0.5f, h * 0.55f)
            }
            drawPath(
                path = leftLeaf,
                color = Color(0x4481C784), // Soft green leaf representation
                style = androidx.compose.ui.graphics.drawscope.Fill
            )

            // Right leaf
            val rightLeaf = Path().apply {
                moveTo(w * 0.5f, h * 0.55f)
                quadraticTo(w * 0.68f, h * 0.45f, w * 0.65f, h * 0.32f)
                quadraticTo(w * 0.52f, h * 0.34f, w * 0.5f, h * 0.55f)
            }
            drawPath(
                path = rightLeaf,
                color = Color(0x4481C784),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
        }

        // Inner Typography layer
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = Color(0xFFFBC02D), // Rich Ochre / Gold
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = "TU",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                color = Color.White,
                letterSpacing = 1.sp
            )

            Text(
                text = "SMKPPN BIMA",
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                color = Color(0xFFDCFCE7), // Soft agricultural emerald tint
                letterSpacing = 0.5.sp
            )
        }
    }
}

