package com.example.smartcity

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.net.wifi.WifiManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.Bundle
import android.content.Context.BLUETOOTH_SERVICE
import android.content.Context.WIFI_SERVICE
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smartcity.ui.theme.SmartCityTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private enum class HomeTab { MAP, DEVICES, PROFILE }

data class UserProfile(
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
    val idToken: String?
)

data class AuthUiState(
    val isLoading: Boolean = false,
    val user: UserProfile? = null,
    val error: String? = null
)

data class DevicePin(
    val name: String,
    val location: GeoPoint
)

class MainActivity : ComponentActivity() {

    private val authState = mutableStateOf(AuthUiState())
    private val allowedDebugEmails = setOf("hoanghuanpham3@gmail.com")

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            handleSignInResult(result.data)
        } else {
            authState.value = authState.value.copy(
                isLoading = false,
                error = "Đăng nhập bị hủy, thử lại nhé."
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        GoogleSignIn.getLastSignedInAccount(this)?.let { account ->
            authState.value = AuthUiState(user = account.toUserProfile())
        }

        setContent {
            SmartCityTheme {
                val uiState by authState
                SmartCityApp(
                    uiState = uiState,
                    onGoogleSignIn = ::launchGoogleSignIn,
                    onSignOut = ::signOut,
                    onContinueAsGuest = ::continueAsGuest
                )
            }
        }
    }

    private fun launchGoogleSignIn() {
        if (BuildConfig.GOOGLE_CLIENT_ID.contains("YOUR_WEB_CLIENT_ID")) {
            authState.value = AuthUiState(
                isLoading = false,
                error = "GOOGLE_CLIENT_ID chưa được cấu hình. Dùng Web client id từ Google Cloud."
            )
            return
        }

        authState.value = authState.value.copy(isLoading = true, error = null)
        val intent = GoogleSignIn.getClient(this, googleSignInOptions()).signInIntent
        signInLauncher.launch(intent)
    }

    private fun signOut() {
        authState.value = authState.value.copy(isLoading = true)
        GoogleSignIn.getClient(this, googleSignInOptions())
            .signOut()
            .addOnCompleteListener {
                authState.value = AuthUiState()
            }
            .addOnFailureListener { error ->
                authState.value = authState.value.copy(
                    isLoading = false,
                    error = error.localizedMessage ?: "Không thể đăng xuất."
                )
        }
    }

    private fun continueAsGuest() {
        authState.value = AuthUiState(
            user = UserProfile(
                displayName = "Khách",
                email = null,
                avatarUrl = null,
                idToken = null
            )
        )
    }

    private fun handleSignInResult(data: Intent?) {
        val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val profile = account.toUserProfile()

            if (!isAllowedEmail(profile.email)) {
                GoogleSignIn.getClient(this, googleSignInOptions()).signOut()
                authState.value = AuthUiState(
                    isLoading = false,
                    error = "Tài khoản ${profile.email ?: "không xác định"} chưa được bật debug."
                )
                return
            }

            authState.value = AuthUiState(user = profile)
            sendIdTokenToBackend(profile.idToken)
        } catch (exception: ApiException) {
            authState.value = AuthUiState(
                isLoading = false,
                error = "Google sign-in lỗi: ${GoogleSignInStatusCodes.getStatusCodeString(exception.statusCode)}"
            )
        }
    }

    private fun googleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_CLIENT_ID)
            .requestEmail()
            .build()
    }

    private fun sendIdTokenToBackend(idToken: String?) {
        // TODO: call your backend here (Retrofit/ktor/OkHttp) with the Google ID token.
        // This is intentionally left light so you can wire the real endpoint later.
    }
}

private fun isAllowedEmail(email: String?): Boolean {
    return email?.lowercase(Locale.US) in setOf("hoanghuanpham3@gmail.com")
}

private fun GoogleSignInAccount.toUserProfile(): UserProfile {
    return UserProfile(
        displayName = displayName ?: "Bạn",
        email = email,
        avatarUrl = photoUrl?.toString(),
        idToken = idToken
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartCityApp(
    uiState: AuthUiState,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val nearbyDevices = remember { mutableStateListOf<DevicePin>() }
    val bluetoothAdapter = remember {
        (context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    }
    val wifiManager = remember {
        context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager?
    }

    var mapCenter by remember { mutableStateOf(GeoPoint(21.028511, 105.804817)) }
    var lastScanBase by remember { mutableStateOf(mapCenter) }

    val locationPermissions = remember {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    val requiredPermissions = remember {
        buildList {
            addAll(locationPermissions)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
    }

    var hasPermissions by rememberSaveable {
        mutableStateOf(hasAllPermissions(context, requiredPermissions))
    }
    var isScanning by rememberSaveable { mutableStateOf(false) }
    val scannedAddresses = remember { mutableStateListOf<String>() }

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    val address = device.address ?: return
                    if (!scannedAddresses.contains(address)) {
                        scannedAddresses.add(address)
                        val name = device.name ?: "Thiết bị BLE"
                        nearbyDevices.add(
                            DevicePin(
                                name = "$name ($address)",
                                location = offsetGeo(lastScanBase, scannedAddresses.size)
                            )
                        )
                    }
                }
            }
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        scope.launch {
            if (denied.isEmpty()) {
                snackbarHostState.showSnackbar("Đã cấp quyền, bắt đầu quét thiết bị.")
                hasPermissions = true
            } else {
                snackbarHostState.showSnackbar("Thiếu quyền: ${denied.joinToString()}")
                hasPermissions = false
            }
        }
    }

    val requestAllPermissions = { permissionsLauncher.launch(requiredPermissions.toTypedArray()) }

    val startScan: () -> Unit = {
        when {
            !hasPermissions -> requestAllPermissions()
            bluetoothAdapter == null -> scope.launch {
                snackbarHostState.showSnackbar("Thiết bị không hỗ trợ Bluetooth LE.")
            }

            !bluetoothAdapter.isEnabled -> scope.launch {
                snackbarHostState.showSnackbar("Bật Bluetooth để quét thiết bị.")
            }

            bluetoothAdapter.bluetoothLeScanner == null -> scope.launch {
                snackbarHostState.showSnackbar("Không thể khởi tạo BLE scanner.")
            }

            else -> {
                lastScanBase = mapCenter
                val scanner = bluetoothAdapter.bluetoothLeScanner!!

                nearbyDevices.clear()
                scannedAddresses.clear()
                isScanning = true
                val base = mapCenter

                val started = try {
                    scanner.startScan(scanCallback)
                    true
                } catch (sec: SecurityException) {
                    scope.launch { snackbarHostState.showSnackbar("Thiếu quyền BLE (SCAN/CONNECT).") }
                    isScanning = false
                    false
                }

                if (started) {
                    val wifiReceiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                                val results = wifiManager?.scanResults.orEmpty()
                                results.take(6).forEachIndexed { idx, result ->
                                    val ssid = if (result.SSID.isNullOrEmpty()) "Wi-Fi ẩn" else result.SSID
                                    val point = offsetGeo(base, idx + scannedAddresses.size)
                                    nearbyDevices.add(DevicePin("Wi-Fi: $ssid", point))
                                }
                                try {
                                    context.unregisterReceiver(this)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }

                    if (wifiManager != null) {
                        try {
                            context.registerReceiver(
                                wifiReceiver,
                                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                            )
                            wifiManager.startScan()
                        } catch (_: Exception) {
                        }
                    }

                    scope.launch {
                        delay(8000)
                        try {
                            scanner.stopScan(scanCallback)
                        } catch (_: Exception) {
                        }
                        isScanning = false
                        if (nearbyDevices.isEmpty()) {
                            snackbarHostState.showSnackbar("Không tìm thấy thiết bị nào (đưa thiết bị vào chế độ pairing).")
                        }
                    }
                } else {
                    isScanning = false
                }
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (uiState.user == null) {
                TopAppBar(
                    title = {
                        Text(
                            text = "SmartCity",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                )
            }
        }
    ) { padding ->
        when {
            uiState.user != null -> HomeScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                user = uiState.user,
                isLoading = uiState.isLoading,
                nearbyDevices = nearbyDevices,
                hasLocationPermission = hasPermissions || hasAllPermissions(context, locationPermissions),
            onSignOut = onSignOut,
            onRequestAllPermissions = requestAllPermissions,
            onStartScan = startScan,
            mapCenter = mapCenter,
            onMapCenterChanged = { mapCenter = it }
        )

            else -> SignInScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                isLoading = uiState.isLoading,
                onGoogleSignIn = onGoogleSignIn,
                onContinueAsGuest = onContinueAsGuest
            )
        }
    }
}

@Composable
private fun SignInScreen(
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    onGoogleSignIn: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(Color(0xFF0F0F0F), Color(0xFF050505))
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Chào mừng",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Đăng nhập bằng Google để xem bản đồ OpenStreetMap.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onGoogleSignIn,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = "Tiếp tục với Google",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onContinueAsGuest,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Vào ngay (Guest)",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    user: UserProfile,
    isLoading: Boolean,
    nearbyDevices: List<DevicePin>,
    onSignOut: () -> Unit,
    hasLocationPermission: Boolean,
    onRequestAllPermissions: () -> Unit,
    onStartScan: () -> Unit,
    mapCenter: GeoPoint,
    onMapCenterChanged: (GeoPoint) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.MAP) }

    Box(modifier = modifier) {
        when (selectedTab) {
            HomeTab.MAP -> MapScreen(
                modifier = Modifier.fillMaxSize(),
                user = user,
                isLoading = isLoading,
                nearbyDevices = nearbyDevices,
                hasLocationPermission = hasLocationPermission,
                onRequestAllPermissions = onRequestAllPermissions,
                onStartScan = onStartScan,
                mapCenter = mapCenter,
                onMapCenterChanged = onMapCenterChanged
            )

            HomeTab.DEVICES -> DevicesScreen(
                modifier = Modifier.fillMaxSize(),
                nearbyDevices = nearbyDevices,
                onStartScan = onStartScan
            )

            HomeTab.PROFILE -> ProfileScreen(
                modifier = Modifier.fillMaxSize(),
                user = user,
                onSignOut = onSignOut
            )
        }

        BottomNavBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )
    }
}

@Composable
private fun BottomNavBar(
    modifier: Modifier = Modifier,
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit
) {
    val items = listOf(
        Triple(HomeTab.MAP, Icons.Filled.Place, "Bản đồ"),
        Triple(HomeTab.DEVICES, Icons.Outlined.DevicesOther, "Thiết bị"),
        Triple(HomeTab.PROFILE, Icons.Filled.Person, "Tài khoản")
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10121B)),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { (tab, icon, label) ->
                val isSelected = tab == selectedTab
                val bg = if (isSelected) Color(0xFF0F4C75) else Color.Transparent
                val fg = if (isSelected) Color.White else Color(0xFF9CA3AF)

                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bg)
                        .clickable { onTabSelected(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = icon, contentDescription = label, tint = fg)
                        Text(
                            text = label,
                            color = fg,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapScreen(
    modifier: Modifier = Modifier,
    user: UserProfile,
    isLoading: Boolean,
    nearbyDevices: List<DevicePin>,
    hasLocationPermission: Boolean,
    onRequestAllPermissions: () -> Unit,
    onStartScan: () -> Unit,
    mapCenter: GeoPoint,
    onMapCenterChanged: (GeoPoint) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = {
                if (!hasLocationPermission) onRequestAllPermissions() else onStartScan()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (hasLocationPermission) "Quét thiết bị" else "Cấp quyền & quét")
        }
        if (nearbyDevices.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Thiết bị gần đây:",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    nearbyDevices.forEach { device ->
                        Text(
                            text = "• ${device.name}",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            OpenStreetMapView(
                modifier = Modifier.fillMaxSize(),
                isLoading = isLoading,
                hasLocationPermission = hasLocationPermission,
                onRequestAllPermissions = onRequestAllPermissions,
                devicePins = nearbyDevices,
                onMapCenterChanged = onMapCenterChanged,
                mapCenter = mapCenter
            )
        }
    }
}

@Composable
private fun DevicesScreen(
    modifier: Modifier = Modifier,
    nearbyDevices: List<DevicePin>,
    onStartScan: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Thiết bị IoT",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Quét Wi-Fi / Bluetooth gần đây để hiển thị trên bản đồ.",
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        )
        Button(onClick = onStartScan, shape = RoundedCornerShape(14.dp)) { Text("Bắt đầu quét") }
        if (nearbyDevices.isEmpty()) {
            Text(
                text = "Chưa có thiết bị nào.",
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                nearbyDevices.forEach { device ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = device.name,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    modifier: Modifier = Modifier,
    user: UserProfile,
    onSignOut: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Tài khoản",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(text = user.displayName, style = MaterialTheme.typography.bodyLarge)
        user.email?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)))
        }
        Button(onClick = onSignOut, modifier = Modifier.padding(top = 8.dp)) {
            Text("Đăng xuất")
        }
    }
}

@Composable
private fun OpenStreetMapView(
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    hasLocationPermission: Boolean,
    onRequestAllPermissions: () -> Unit,
    devicePins: List<DevicePin>,
    onMapCenterChanged: (GeoPoint) -> Unit,
    mapCenter: GeoPoint
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.5)
            controller.setCenter(mapCenter)
        }
    }

    val locationOverlay = remember(mapView) {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
    }

    DisposableEffect(mapView) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                mapView.mapCenter?.let { center ->
                    onMapCenterChanged(GeoPoint(center.latitude, center.longitude))
                }
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                mapView.mapCenter?.let { center ->
                    onMapCenterChanged(GeoPoint(center.latitude, center.longitude))
                }
                return false
            }
        }
        mapView.addMapListener(listener)
        onDispose { mapView.removeMapListener(listener) }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            if (!mapView.overlays.contains(locationOverlay)) {
                mapView.overlays.add(0, locationOverlay)
            }
            locationOverlay.enableMyLocation()
            locationOverlay.enableFollowLocation()
            locationOverlay.myLocation?.let { mapView.controller.animateTo(it) }
        } else {
            locationOverlay.disableMyLocation()
            mapView.overlays.remove(locationOverlay)
        }
    }

    val deviceMarkers = remember { mutableListOf<org.osmdroid.views.overlay.Marker>() }

    LaunchedEffect(devicePins) {
        mapView.overlays.removeAll(deviceMarkers)
        deviceMarkers.clear()

        devicePins.forEach { pin ->
            val marker = org.osmdroid.views.overlay.Marker(mapView).apply {
                position = pin.location
                title = pin.name
                setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
            }
            deviceMarkers.add(marker)
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        FloatingActionButton(
            onClick = {
                    if (!hasLocationPermission) {
                        onRequestAllPermissions()
                    } else {
                        locationOverlay.myLocation?.let {
                            mapView.controller.animateTo(it)
                            mapView.controller.setZoom(16.0)
                        } ?: run {
                            mapView.controller.animateTo(mapCenter)
                        }
                    }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 96.dp),
            containerColor = Color(0xFF0F4C75),
            contentColor = Color.White
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = "Về vị trí của tôi"
            )
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
            )
        }
    }
}

private fun configureOsmdroid(context: android.content.Context) {
    val cacheBase = File(context.cacheDir, "osmdroid")
    val tileCache = File(cacheBase, "tiles")
    if (!tileCache.exists()) {
        tileCache.mkdirs()
    }
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = cacheBase
        osmdroidTileCache = tileCache
    }
}

private fun offsetGeo(base: GeoPoint, index: Int): GeoPoint {
    val latOffset = Random.nextDouble(-0.002, 0.002) + (index % 3) * 0.0004
    val lonOffset = Random.nextDouble(-0.002, 0.002) + (index % 3) * 0.0004
    return GeoPoint(base.latitude + latOffset, base.longitude + lonOffset)
}

private fun hasAllPermissions(context: android.content.Context, permissions: List<String>): Boolean {
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PERMISSION_GRANTED
    }
}
