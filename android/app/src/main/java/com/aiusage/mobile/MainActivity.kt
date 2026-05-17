package com.aiusage.mobile

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aiusage.mobile.ads.AdConsentManager
import com.aiusage.mobile.localization.withAppLanguageForDeviceLanguage
import com.aiusage.mobile.notification.UsageLimitNotificationController
import com.aiusage.mobile.sync.ForegroundRefreshController
import com.aiusage.mobile.sync.SnapshotDevice
import com.aiusage.mobile.sync.SnapshotProviderUsage
import com.aiusage.mobile.sync.SnapshotUsageLimitLine
import com.aiusage.mobile.sync.SnapshotRefreshResult
import com.aiusage.mobile.sync.SnapshotRepository
import com.aiusage.mobile.sync.SnapshotStatus
import com.aiusage.mobile.ui.AIUsageAppShell
import com.aiusage.mobile.ui.AIUsageColors
import com.aiusage.mobile.ui.AppRoute
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.common.api.ApiException
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val WindowsAppBackground = AIUsageColors.SurfaceMuted
private val PanelColor = AIUsageColors.SurfaceMuted
private val InkColor = AIUsageColors.TextPrimary
private val MutedColor = AIUsageColors.TextSecondary
private val DividerColor = AIUsageColors.BorderDefault.copy(alpha = 0.32f)
private val GaugeTrackColor = AIUsageColors.WindowChrome
private val GaugeFillColor = AIUsageColors.SurfaceStrong
private val BrandPurple = AIUsageColors.SurfaceStrong
private val SuccessColor = Color(0xFF22C55E)
private val WarningColor = AIUsageColors.SurfaceRaised
private val DangerColor = Color(0xFFEF4444)
private val HeaderTopOffset = 22.dp
private val WindowsCardShape = RoundedCornerShape(10.dp)
private val PillShape = RoundedCornerShape(999.dp)

private val PretendardFontFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold)
)

private val AIUsageTypography = Typography().withAppFont(PretendardFontFamily)

private fun Typography.withAppFont(fontFamily: FontFamily): Typography {
    return copy(
        displayLarge = displayLarge.copy(fontFamily = fontFamily),
        displayMedium = displayMedium.copy(fontFamily = fontFamily),
        displaySmall = displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = titleLarge.copy(fontFamily = fontFamily),
        titleMedium = titleMedium.copy(fontFamily = fontFamily),
        titleSmall = titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = bodySmall.copy(fontFamily = fontFamily),
        labelLarge = labelLarge.copy(fontFamily = fontFamily),
        labelMedium = labelMedium.copy(fontFamily = fontFamily),
        labelSmall = labelSmall.copy(fontFamily = fontFamily)
    )
}

class MainActivity : ComponentActivity() {
    private val repository by lazy { SnapshotRepository(applicationContext) }
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLanguageForDeviceLanguage())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AIUsageColors.MacOSCanvas.toArgb()
        window.navigationBarColor = AIUsageColors.MacOSCanvas.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                systemUiVisibility = systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            window.decorView.systemUiVisibility = systemUiVisibility
        }
        val initialRoute = AppRoute.fromExtras(
            route = intent?.getStringExtra(AppRoute.EXTRA_ROUTE),
            providerIdStorageId = intent?.getStringExtra(AppRoute.EXTRA_PROVIDER_ID),
            legacyProviderIdStorageId = intent?.getStringExtra(AppRoute.EXTRA_PROVIDER_ID_LEGACY)
        )
        UsageLimitNotificationController.updateFromCache(applicationContext)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = BrandPurple,
                    onPrimary = AIUsageColors.SurfaceMuted,
                    primaryContainer = AIUsageColors.SurfaceRaised,
                    onPrimaryContainer = AIUsageColors.BorderDefault,
                    secondary = AIUsageColors.SurfaceRaised,
                    background = WindowsAppBackground,
                    surface = PanelColor,
                    surfaceVariant = AIUsageColors.WindowChrome,
                    onSurface = InkColor,
                    onSurfaceVariant = AIUsageColors.TextSecondary,
                    outline = AIUsageColors.BorderDefault,
                    outlineVariant = AIUsageColors.BorderDefault.copy(alpha = 0.48f),
                    error = DangerColor
                ),
                typography = AIUsageTypography
            ) {
                AIUsageAppShell(
                    context = applicationContext,
                    initialRoute = initialRoute
                )
            }
        }
    }

    fun googleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(getString(R.string.default_web_client_id))
            .build()
    }

    companion object {
        fun createHomeIntent(context: Context): Intent {
            return routeIntent(context, AppRoute.ROUTE_HOME)
        }

        fun createProviderDetailIntent(context: Context, providerIdStorageId: String): Intent {
            return routeIntent(context, AppRoute.ROUTE_PROVIDER)
                .putExtra(AppRoute.EXTRA_PROVIDER_ID, providerIdStorageId)
                .putExtra(AppRoute.EXTRA_PROVIDER_ID_LEGACY, providerIdStorageId)
        }

        private fun routeIntent(context: Context, route: String): Intent {
            return Intent(context, MainActivity::class.java)
                .putExtra(AppRoute.EXTRA_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }
}

@Composable
fun AIUsageApp(
    activity: MainActivity,
    auth: FirebaseAuth,
    repository: SnapshotRepository
) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var signingIn by remember { mutableStateOf(false) }
    var refreshingSnapshot by remember { mutableStateOf(false) }
    var snapshotResult by remember { mutableStateOf<SnapshotRefreshResult?>(null) }
    var snapshotMessage by remember { mutableStateOf<String?>(null) }
    var deviceList by remember { mutableStateOf<List<SnapshotDevice>>(emptyList()) }
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var canPostNotifications by remember { mutableStateOf(UsageLimitNotificationController.canPostNotifications(activity)) }
    var notificationEnabled by remember {
        mutableStateOf(UsageLimitNotificationController.isEnabled(activity) && canPostNotifications)
    }
    val foregroundRefreshController = remember { ForegroundRefreshController(activity.applicationContext) }
    var preciseRefreshEnabled by remember { mutableStateOf(foregroundRefreshController.preciseRefreshEnabled()) }
    var preciseRefreshPromptSeen by remember { mutableStateOf(foregroundRefreshController.preciseRefreshPromptSeen()) }
    val adConsentManager = remember { AdConsentManager(activity.applicationContext) }
    var canRequestAds by remember { mutableStateOf(BuildConfig.ADS_ENABLED && adConsentManager.canRequestAds) }
    var privacyOptionsRequired by remember {
        mutableStateOf(BuildConfig.ADS_ENABLED && adConsentManager.privacyOptionsRequired)
    }
    var mobileAdsInitialized by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun refreshAdConsentState() {
        canRequestAds = BuildConfig.ADS_ENABLED && adConsentManager.canRequestAds
        privacyOptionsRequired = BuildConfig.ADS_ENABLED && adConsentManager.privacyOptionsRequired
    }

    fun initializeMobileAdsIfReady() {
        if (!BuildConfig.ADS_ENABLED || !canRequestAds || mobileAdsInitialized) {
            return
        }
        MobileAds.initialize(activity.applicationContext) {
            mobileAdsInitialized = true
        }
    }

    fun refreshNotificationState() {
        canPostNotifications = UsageLimitNotificationController.canPostNotifications(activity)
        notificationEnabled = UsageLimitNotificationController.isEnabled(activity) && canPostNotifications
        if (!canPostNotifications && preciseRefreshEnabled) {
            preciseRefreshEnabled = false
            foregroundRefreshController.setPreciseRefreshEnabled(false)
        }
    }

    fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${activity.packageName}"))
        }
        activity.startActivity(intent)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        canPostNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted
        notificationEnabled = granted
        UsageLimitNotificationController.setEnabled(activity, granted)
        if (granted) {
            UsageLimitNotificationController.updateFromCache(activity)
            if (foregroundRefreshController.preciseRefreshEnabled() || preciseRefreshEnabled) {
                preciseRefreshEnabled = true
                foregroundRefreshController.setPreciseRefreshEnabled(true)
            }
        } else {
            preciseRefreshEnabled = false
            foregroundRefreshController.setPreciseRefreshEnabled(false)
        }
    }

    fun enablePreciseRefresh() {
        foregroundRefreshController.markPreciseRefreshPromptSeen()
        preciseRefreshPromptSeen = true
        preciseRefreshEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !UsageLimitNotificationController.canPostNotifications(activity)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        notificationEnabled = true
        UsageLimitNotificationController.setEnabled(activity, true)
        foregroundRefreshController.setPreciseRefreshEnabled(true)
    }

    fun disablePreciseRefresh() {
        foregroundRefreshController.markPreciseRefreshPromptSeen()
        preciseRefreshPromptSeen = true
        preciseRefreshEnabled = false
        foregroundRefreshController.setPreciseRefreshEnabled(false)
    }

    fun loadDevices(uid: String) {
        coroutineScope.launch {
            val devices = repository.listDevices(uid)
            deviceList = devices
            val selected = selectedDeviceId?.takeIf { id -> devices.any { it.deviceId == id } }
                ?: devices.firstOrNull()?.deviceId
            selectedDeviceId = selected
            renameDraft = devices.firstOrNull { it.deviceId == selected }?.deviceName.orEmpty()
        }
    }

    fun refreshLatestSnapshot(uid: String) {
        refreshingSnapshot = true
        snapshotMessage = null
        coroutineScope.launch {
            try {
                val result = repository.refreshLatestSnapshot(uid, selectedDeviceId)
                snapshotResult = result
                selectedDeviceId = result.deviceId ?: selectedDeviceId
                snapshotMessage = result.message
                loadDevices(uid)
            } catch (error: Throwable) {
                snapshotMessage = error.message ?: "Could not refresh latest snapshot"
            } finally {
                refreshingSnapshot = false
            }
        }
    }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            signingIn = false
            authMessage = "Google sign-in was cancelled"
            return@rememberLauncherForActivityResult
        }

        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    signingIn = false
                    authMessage = null
                    currentUser = auth.currentUser
                }
                .addOnFailureListener { error ->
                    signingIn = false
                    authMessage = error.message ?: "Google sign-in failed"
                }
        } catch (error: ApiException) {
            signingIn = false
            authMessage = error.localizedMessage ?: "Google sign-in failed"
        }
    }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
            if (firebaseAuth.currentUser == null) {
                snapshotResult = null
                snapshotMessage = null
                deviceList = emptyList()
                selectedDeviceId = null
                renameDraft = ""
            }
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshNotificationState()
                refreshAdConsentState()
                initializeMobileAdsIfReady()
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (BuildConfig.ADS_ENABLED) {
            adConsentManager.gatherConsent(activity) {
                refreshAdConsentState()
                initializeMobileAdsIfReady()
            }
        }
    }

    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid ?: return@LaunchedEffect
        refreshNotificationState()
        repository.rememberSignedInUser(uid)
        loadDevices(uid)
        refreshLatestSnapshot(uid)
        repository.enqueueRefresh(uid)
        repository.scheduleWidgetRefresh(uid)
        if (foregroundRefreshController.preciseRefreshEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || UsageLimitNotificationController.canPostNotifications(activity))
        ) {
            foregroundRefreshController.startPreciseRefresh()
        }
        while (isActive) {
            delay(60_000)
            refreshLatestSnapshot(uid)
        }
    }

    if (currentUser == null) {
        LoginScreen(
            signingIn = signingIn,
            authMessage = authMessage,
            onGoogleSignIn = {
                signingIn = true
                authMessage = null
                val googleIntent = GoogleSignIn.getClient(
                    activity,
                    activity.googleSignInOptions()
                ).signInIntent
                googleLauncher.launch(googleIntent)
            }
        )
        return
    }

    val showFixedAdBanner = !showSettings && canRequestAds && mobileAdsInitialized

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WindowsAppBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = if (showFixedAdBanner) 96.dp else 20.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SignedInContent(
                user = currentUser,
                deviceList = deviceList,
                selectedDeviceId = selectedDeviceId,
                renameDraft = renameDraft,
                snapshotResult = snapshotResult,
                snapshotMessage = snapshotMessage,
                refreshingSnapshot = refreshingSnapshot,
                showSettings = showSettings,
                notificationEnabled = notificationEnabled,
                canPostNotifications = canPostNotifications,
                preciseRefreshEnabled = preciseRefreshEnabled,
                preciseRefreshPromptSeen = preciseRefreshPromptSeen,
                privacyOptionsRequired = privacyOptionsRequired,
                onPreciseRefreshEnabledChanged = { enabled ->
                    if (enabled) {
                        enablePreciseRefresh()
                    } else {
                        disablePreciseRefresh()
                    }
                },
                onDismissPreciseRefreshPrompt = {
                    foregroundRefreshController.markPreciseRefreshPromptSeen()
                    preciseRefreshPromptSeen = true
                },
                onNotificationEnabledChanged = { enabled ->
                    if (!enabled) {
                        notificationEnabled = false
                        UsageLimitNotificationController.setEnabled(activity, false)
                        return@SignedInContent
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !UsageLimitNotificationController.canPostNotifications(activity)
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        notificationEnabled = true
                        UsageLimitNotificationController.setEnabled(activity, true)
                        UsageLimitNotificationController.updateFromCache(activity)
                    }
                },
                onOpenNotificationSettings = {
                    openNotificationSettings()
                },
                onPrivacyChoices = {
                    adConsentManager.showPrivacyOptionsForm(activity) {
                        refreshAdConsentState()
                        initializeMobileAdsIfReady()
                    }
                },
                onSelectDevice = { device ->
                    selectedDeviceId = device.deviceId
                    renameDraft = device.deviceName
                    currentUser?.uid?.let(::refreshLatestSnapshot)
                },
                onRenameDraftChanged = { renameDraft = it },
                onSaveDeviceName = {
                    val uid = currentUser?.uid ?: return@SignedInContent
                    val deviceId = selectedDeviceId ?: return@SignedInContent
                    coroutineScope.launch {
                        try {
                            repository.updateDeviceName(uid, deviceId, renameDraft)
                            snapshotMessage = "Device name updated"
                            loadDevices(uid)
                            refreshLatestSnapshot(uid)
                        } catch (error: Throwable) {
                            snapshotMessage = error.message ?: "Could not update device name"
                        }
                    }
                },
                onDeleteDevice = { device ->
                    val uid = currentUser?.uid ?: return@SignedInContent
                    coroutineScope.launch {
                        try {
                            repository.deleteDevice(uid, device.deviceId)
                            if (selectedDeviceId == device.deviceId) {
                                selectedDeviceId = null
                                renameDraft = ""
                                snapshotResult = null
                            }
                            snapshotMessage = activity.getString(R.string.settings_device_deleted)
                            loadDevices(uid)
                            refreshLatestSnapshot(uid)
                        } catch (error: Throwable) {
                            snapshotMessage = error.message ?: activity.getString(R.string.settings_delete_device_failed)
                        }
                    }
                },
                onRefreshSnapshot = {
                    currentUser?.uid?.let(::refreshLatestSnapshot)
                },
                onToggleSettings = {
                    showSettings = !showSettings
                },
                onSignOut = {
                    auth.signOut()
                    GoogleSignIn.getClient(activity, activity.googleSignInOptions()).signOut()
                    repository.clearSignedInUser()
                    foregroundRefreshController.stopPreciseRefresh()
                    preciseRefreshEnabled = false
                    signingIn = false
                    authMessage = null
                    snapshotResult = null
                    snapshotMessage = null
                    deviceList = emptyList()
                    selectedDeviceId = null
                    renameDraft = ""
                    showSettings = false
                }
            )
        }

        if (showFixedAdBanner) {
            AdBannerBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun LoginScreen(
    signingIn: Boolean,
    authMessage: String?,
    onGoogleSignIn: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WindowsAppBackground)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(132.dp),
                shape = RoundedCornerShape(34.dp),
                color = PanelColor,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, DividerColor)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = "AI Usage icon",
                        modifier = Modifier.size(106.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "AI Usage",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = InkColor
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Sign in to sync your usage limits",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedColor
            )
            Spacer(modifier = Modifier.height(28.dp))
            GoogleButtonSurface(
                signingIn = signingIn,
                onClick = {
                    if (!signingIn) onGoogleSignIn()
                }
            )
            authMessage?.let {
                Spacer(modifier = Modifier.height(18.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = DangerColor)
            }
        }
    }
}

@Composable
private fun GoogleButtonSurface(
    signingIn: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !signingIn,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = PillShape,
        border = BorderStroke(1.dp, DividerColor),
        colors = ButtonDefaults.buttonColors(
            containerColor = PanelColor,
            contentColor = InkColor,
            disabledContainerColor = PanelColor,
            disabledContentColor = MutedColor
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp, pressedElevation = 0.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_google_g),
            contentDescription = "Google",
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(if (signingIn) "Signing in..." else "Continue with Google", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SignedInContent(
    user: FirebaseUser?,
    deviceList: List<SnapshotDevice>,
    selectedDeviceId: String?,
    renameDraft: String,
    snapshotResult: SnapshotRefreshResult?,
    snapshotMessage: String?,
    refreshingSnapshot: Boolean,
    showSettings: Boolean,
    notificationEnabled: Boolean,
    canPostNotifications: Boolean,
    preciseRefreshEnabled: Boolean,
    preciseRefreshPromptSeen: Boolean,
    privacyOptionsRequired: Boolean,
    onPreciseRefreshEnabledChanged: (Boolean) -> Unit,
    onDismissPreciseRefreshPrompt: () -> Unit,
    onNotificationEnabledChanged: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onPrivacyChoices: () -> Unit,
    onSelectDevice: (SnapshotDevice) -> Unit,
    onRenameDraftChanged: (String) -> Unit,
    onSaveDeviceName: () -> Unit,
    onDeleteDevice: (SnapshotDevice) -> Unit,
    onRefreshSnapshot: () -> Unit,
    onToggleSettings: () -> Unit,
    onSignOut: () -> Unit
) {
    AppHeader(showSettings = showSettings, onToggleSettings = onToggleSettings)

    if (!preciseRefreshPromptSeen) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = PanelColor,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, DividerColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.precise_refresh_prompt_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.precise_refresh_prompt_body), color = MutedColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onPreciseRefreshEnabledChanged(true) },
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                    ) {
                        Text(stringResource(R.string.precise_refresh_enable))
                    }
                    Button(
                        onClick = onDismissPreciseRefreshPrompt,
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(containerColor = GaugeFillColor)
                    ) {
                        Text(stringResource(R.string.precise_refresh_standard))
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsPanel(
            user = user,
            deviceList = deviceList,
            selectedDeviceId = selectedDeviceId,
            renameDraft = renameDraft,
            snapshotResult = snapshotResult,
            snapshotMessage = snapshotMessage,
            refreshingSnapshot = refreshingSnapshot,
            notificationEnabled = notificationEnabled,
            canPostNotifications = canPostNotifications,
            preciseRefreshEnabled = preciseRefreshEnabled,
            privacyOptionsRequired = privacyOptionsRequired,
            onPreciseRefreshEnabledChanged = onPreciseRefreshEnabledChanged,
            onNotificationEnabledChanged = onNotificationEnabledChanged,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onPrivacyChoices = onPrivacyChoices,
            onSelectDevice = onSelectDevice,
            onRenameDraftChanged = onRenameDraftChanged,
            onSaveDeviceName = onSaveDeviceName,
            onDeleteDevice = onDeleteDevice,
            onRefreshSnapshot = onRefreshSnapshot,
            onSignOut = onSignOut
        )
    } else {
        LimitDashboard(
            snapshotResult = snapshotResult,
            snapshotMessage = snapshotMessage,
            refreshingSnapshot = refreshingSnapshot,
            onRefreshSnapshot = onRefreshSnapshot
        )
    }
}

@Composable
private fun AppHeader(
    showSettings: Boolean,
    onToggleSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HeaderTopOffset),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("AI Usage", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = InkColor)
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onToggleSettings,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
        ) {
            Text(if (showSettings) stringResource(R.string.settings_close) else "⚙ ${stringResource(R.string.settings_open)}")
        }
    }
}

@Composable
private fun LimitDashboard(
    snapshotResult: SnapshotRefreshResult?,
    snapshotMessage: String?,
    refreshingSnapshot: Boolean,
    onRefreshSnapshot: () -> Unit
) {
    Text("Usage Limits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = InkColor)
    Text(snapshotResult?.status?.name ?: SnapshotStatus.NotLinked.name, color = MutedColor)

    if (snapshotResult?.providers.isNullOrEmpty()) {
        Text(snapshotMessage ?: "No active AI usage limits to show")
    } else {
        snapshotResult.providers.forEach { provider ->
            ProviderLimitCard(provider)
        }
    }

    snapshotResult?.updatedAt?.takeIf { it.isNotBlank() }?.let {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Updates soon", color = MutedColor)
            Spacer(modifier = Modifier.weight(1f))
            Text("Updated at $it", color = MutedColor)
        }
    }

    Button(
        onClick = onRefreshSnapshot,
        modifier = Modifier.fillMaxWidth(),
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
    ) {
        Text(
            if (refreshingSnapshot) {
                stringResource(R.string.settings_refreshing_snapshot)
            } else {
                stringResource(R.string.settings_refresh_latest_snapshot)
            }
        )
    }
}

@Composable
private fun AdBannerBottomBar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = WindowsAppBackground,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            AdBanner(
                modifier = Modifier.fillMaxWidth(),
                adUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID,
                canRequestAds = true
            )
        }
    }
}

@Composable
private fun AdBanner(
    modifier: Modifier = Modifier,
    adUnitId: String,
    canRequestAds: Boolean
) {
    if (!BuildConfig.ADS_ENABLED || adUnitId.isBlank() || !canRequestAds) {
        return
    }

    val context = LocalContext.current
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val adWidthDp = maxWidth.value.toInt()
        if (adWidthDp > 0) {
            val adView = remember(adUnitId, adWidthDp) {
                AdView(context).apply {
                    this.adUnitId = adUnitId
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp))
                }
            }
            DisposableEffect(adView) {
                onDispose { adView.destroy() }
            }
            LaunchedEffect(adView, canRequestAds) {
                val extras = Bundle().apply {
                    putString("npa", "1")
                }
                val adRequest = AdRequest.Builder()
                    .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                    .build()
                adView.loadAd(adRequest)
            }
            AndroidView(
                factory = { adView },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ProviderLimitCard(provider: SnapshotProviderUsage) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = WindowsCardShape,
        color = PanelColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(provider.providerName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = InkColor)
                Spacer(modifier = Modifier.weight(1f))
                provider.plan?.takeIf { it.isNotBlank() }?.let {
                    PlanPill(it)
                }
            }
            provider.lines.forEach { line ->
                ProviderLimitLine(line)
            }
            if (provider.status != "ok") {
                Text(provider.status, color = DangerColor)
            }
        }
    }
}

@Composable
private fun PlanPill(plan: String) {
    Surface(
        shape = PillShape,
        color = PanelColor,
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Text(
            text = plan,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = Color(0xFF475569),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ProviderLimitLine(line: SnapshotUsageLimitLine) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line.label, style = MaterialTheme.typography.bodyLarge, color = InkColor)
            Spacer(modifier = Modifier.size(6.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(PillShape)
                    .background(limitIndicatorColor(line.remainingRatio))
            )
            Spacer(modifier = Modifier.weight(1f))
            line.resetText?.let { Text(it, color = MutedColor) }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(GaugeTrackColor)
        ) {
            LinearProgressIndicator(
                progress = { line.remainingRatio ?: 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = GaugeFillColor,
                trackColor = Color.Transparent
            )
        }
        Text(line.remainingText, color = MutedColor)
    }
}

private fun limitIndicatorColor(ratio: Float?): Color {
    val value = ratio ?: 0f
    return when {
        value < 0.15f -> DangerColor
        value < 0.35f -> WarningColor
        else -> SuccessColor
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsPanel(
    user: FirebaseUser?,
    deviceList: List<SnapshotDevice>,
    selectedDeviceId: String?,
    renameDraft: String,
    snapshotResult: SnapshotRefreshResult?,
    snapshotMessage: String?,
    refreshingSnapshot: Boolean,
    notificationEnabled: Boolean,
    canPostNotifications: Boolean,
    preciseRefreshEnabled: Boolean,
    privacyOptionsRequired: Boolean,
    onPreciseRefreshEnabledChanged: (Boolean) -> Unit,
    onNotificationEnabledChanged: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onPrivacyChoices: () -> Unit,
    onSelectDevice: (SnapshotDevice) -> Unit,
    onRenameDraftChanged: (String) -> Unit,
    onSaveDeviceName: () -> Unit,
    onDeleteDevice: (SnapshotDevice) -> Unit,
    onRefreshSnapshot: () -> Unit,
    onSignOut: () -> Unit
) {
    var deleteCandidateDeviceId by remember(deviceList) { mutableStateOf<String?>(null) }

    Text(user?.email ?: user?.uid.orEmpty(), style = MaterialTheme.typography.bodyMedium)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_status_bar_gauges), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_status_bar_gauges_description), color = Color(0xFF64748B))
        }
        Switch(
            checked = notificationEnabled,
            onCheckedChange = onNotificationEnabledChanged
        )
    }
    if (!canPostNotifications) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFFBEB),
            border = BorderStroke(1.dp, Color(0xFFFDE68A))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.settings_notifications_permission_required), color = Color(0xFF92400E))
                Button(
                    onClick = onOpenNotificationSettings,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                ) {
                    Text(stringResource(R.string.settings_open_notification_settings))
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_precise_refresh), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_precise_refresh_description), color = Color(0xFF64748B))
        }
        Switch(
            checked = preciseRefreshEnabled,
            onCheckedChange = onPreciseRefreshEnabledChanged
        )
    }

    if (privacyOptionsRequired) {
        Button(onClick = onPrivacyChoices, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_privacy_choices))
        }
    }

    Text(stringResource(R.string.settings_connected_devices), style = MaterialTheme.typography.titleMedium)
    if (deviceList.isEmpty()) {
        Text(stringResource(R.string.settings_no_pc_linked))
    } else {
        deviceList.forEach { device ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PillShape)
                        .combinedClickable(
                            onClick = {
                                deleteCandidateDeviceId = null
                                onSelectDevice(device)
                            },
                            onLongClick = {
                                deleteCandidateDeviceId = device.deviceId
                                onSelectDevice(device)
                            }
                        ),
                    shape = PillShape,
                    color = BrandPurple,
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(device.deviceName, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(device.status.name, fontWeight = FontWeight.Bold)
                    }
                }
                if (deleteCandidateDeviceId == device.deviceId) {
                    Button(
                        onClick = {
                            deleteCandidateDeviceId = null
                            onDeleteDevice(device)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DangerColor)
                    ) {
                        Text(stringResource(R.string.settings_delete_device))
                    }
                }
            }
        }
    }

    Text(stringResource(R.string.settings_selected_device), style = MaterialTheme.typography.titleMedium)
    Text(snapshotResult?.deviceName ?: deviceList.firstOrNull { it.deviceId == selectedDeviceId }?.deviceName ?: stringResource(R.string.settings_no_pc_linked))

    Text(stringResource(R.string.settings_rename_selected_device), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = renameDraft,
        onValueChange = onRenameDraftChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.settings_device_name)) }
    )
    Button(
        onClick = onSaveDeviceName,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.settings_save_device_name))
    }

    Text(stringResource(R.string.settings_snapshot_status), style = MaterialTheme.typography.titleMedium)
    Text(snapshotResult?.status?.name ?: SnapshotStatus.NotLinked.name)

    Button(onClick = onRefreshSnapshot, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (refreshingSnapshot) {
                stringResource(R.string.settings_refreshing_snapshot)
            } else {
                stringResource(R.string.settings_refresh_latest_snapshot)
            }
        )
    }

    snapshotMessage?.let { Text(it) }

    Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_sign_out))
    }
}
