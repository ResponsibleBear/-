package ru.alicedom.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import ru.alicedom.app.data.*
import ru.alicedom.app.widget.AliceSwitchesWidgetProvider

enum class DeviceCategory { LIGHT, CLIMATE, POWER, MEDIA, SENSOR, APPLIANCE }

private fun classifyDevice(type: String): DeviceCategory = when {
    type.startsWith("devices.types.light") -> DeviceCategory.LIGHT
    type.startsWith("devices.types.thermostat") ||
        type.startsWith("devices.types.humidifier") ||
        type.startsWith("devices.types.purifier") ||
        type.startsWith("devices.types.ventilation") -> DeviceCategory.CLIMATE
    type.startsWith("devices.types.socket") || type.startsWith("devices.types.switch") -> DeviceCategory.POWER
    type.startsWith("devices.types.media_device") ||
        type.startsWith("devices.types.smart_speaker") ||
        type.startsWith("devices.types.camera") -> DeviceCategory.MEDIA
    type.startsWith("devices.types.sensor") -> DeviceCategory.SENSOR
    else -> DeviceCategory.APPLIANCE
}

private fun iconForDevice(type: String): ImageVector = when {
    type.startsWith("devices.types.light") -> Icons.Rounded.Lightbulb
    type.startsWith("devices.types.thermostat.ac") -> Icons.Rounded.AcUnit
    type.startsWith("devices.types.thermostat") -> Icons.Rounded.Thermostat
    type.startsWith("devices.types.humidifier") || type.startsWith("devices.types.purifier") -> Icons.Rounded.Air
    type.startsWith("devices.types.socket") -> Icons.Rounded.Power
    type.startsWith("devices.types.switch") -> Icons.Rounded.ToggleOn
    type.startsWith("devices.types.openable.curtain") -> Icons.Rounded.Blinds
    type.startsWith("devices.types.media_device.tv") -> Icons.Rounded.Tv
    type.startsWith("devices.types.smart_speaker") -> Icons.Rounded.Speaker
    type.startsWith("devices.types.camera") -> Icons.Rounded.Videocam
    type.startsWith("devices.types.cooking.kettle") -> Icons.Rounded.LocalCafe
    type.startsWith("devices.types.vacuum_cleaner") -> Icons.Rounded.CleaningServices
    type.startsWith("devices.types.sensor") -> Icons.Rounded.Sensors
    else -> Icons.Rounded.PowerSettingsNew
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val tokenStore = SecureTokenStore(applicationContext)
        val prefsStore = DevicePrefsStore(applicationContext)
        val apiClient = YandexSmartHomeClient()

        setContent {
            val darkTheme = isSystemInDarkTheme()
            val scheme = if (darkTheme) {
                darkColorScheme(
                    primary = Color(0xFF3B82F6),
                    onPrimary = Color.White,
                    background = Color(0xFF090B10),
                    surface = Color(0xFF141923),
                    surfaceVariant = Color(0xFF1D2433),
                    onBackground = Color(0xFFF3F6FC),
                    onSurface = Color(0xFFF3F6FC),
                    onSurfaceVariant = Color(0xFF9AA5BA),
                    outline = Color(0xFF283246)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF2563EB),
                    onPrimary = Color.White,
                    background = Color(0xFFEEF1F7),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFE2E8F2),
                    onBackground = Color(0xFF0F172A),
                    onSurface = Color(0xFF0F172A),
                    onSurfaceVariant = Color(0xFF526077),
                    outline = Color(0xFFD5DCE8)
                )
            }

            MaterialTheme(colorScheme = scheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AliceDomScreen(
                        tokenStore = tokenStore,
                        prefsStore = prefsStore,
                        apiClient = apiClient,
                        isDark = darkTheme
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AliceDomScreen(
    tokenStore: SecureTokenStore,
    prefsStore: DevicePrefsStore,
    apiClient: YandexSmartHomeClient,
    isDark: Boolean
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var token by remember { mutableStateOf(tokenStore.getToken()) }
    var snapshot by remember { mutableStateOf(if (token != null) prefsStore.loadCachedHome() else null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedDeviceForMenu by remember { mutableStateOf<SmartDevice?>(null) }
    var showManageSheet by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var prefsVersion by remember { mutableIntStateOf(0) }
    var lastRefreshMs by remember { mutableLongStateOf(0L) }
    val pagerState = rememberPagerState(pageCount = { 2 })

    fun syncWidgetsAndTiles(data: SmartHomeSnapshot?) {
        if (data != null) prefsStore.saveCachedHome(data)
        AliceSwitchesWidgetProvider.updateAllWidgets(context, data)
        AliceSwitchesWidgetProvider.requestTilesRefresh(context)
    }

    fun refresh(silent: Boolean = false) {
        val t = token ?: return
        scope.launch {
            if (!silent) isRefreshing = true
            errorMsg = null
            lastRefreshMs = System.currentTimeMillis()
            apiClient.fetchHome(t)
                .onSuccess { data ->
                    snapshot = data
                    syncWidgetsAndTiles(data)
                }
                .onFailure { errorMsg = it.message }
            isRefreshing = false
        }
    }

    LaunchedEffect(token) {
        if (token != null) refresh(silent = snapshot != null)
    }

    // Auto-refresh whenever returning to app from background
    DisposableEffect(lifecycleOwner, token) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && token != null) {
                val now = System.currentTimeMillis()
                if (now - lastRefreshMs > 1500L) {
                    refresh(silent = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (token == null) {
        LoginScreen(
            initialClientId = tokenStore.getClientId(),
            isLoading = isRefreshing,
            errorMsg = errorMsg,
            onOpenOAuth = { cid ->
                tokenStore.saveClientId(cid)
                val url = "https://oauth.yandex.ru/authorize?response_type=token&client_id=${Uri.encode(cid)}&force_confirm=yes"
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            },
            onSubmitToken = { raw ->
                scope.launch {
                    isRefreshing = true
                    errorMsg = null
                    apiClient.fetchHome(raw)
                        .onSuccess { data ->
                            tokenStore.saveToken(raw)
                            token = raw
                            snapshot = data
                            syncWidgetsAndTiles(data)
                        }
                        .onFailure { errorMsg = it.message }
                    isRefreshing = false
                }
            }
        )
        return
    }

    val home = snapshot
    val roomMap = remember(home) { home?.rooms?.associateBy { it.id } ?: emptyMap() }
    val allDevices = home?.devices ?: emptyList()

    val favoriteIds = remember(prefsVersion) { prefsStore.getFavoriteIds().toSet() }
    val hiddenIds = remember(prefsVersion) { prefsStore.getHiddenIds() }

    val switchDevices = remember(allDevices, prefsVersion, showHidden) {
        prefsStore.sortAndFilter(
            devices = allDevices.filter { it.type.startsWith("devices.types.switch") },
            showHidden = showHidden
        )
    }
    val otherDevices = remember(allDevices, prefsVersion, showHidden) {
        prefsStore.sortAndFilter(
            devices = allDevices.filter { !it.type.startsWith("devices.types.switch") },
            showHidden = showHidden
        )
    }

    val currentPageDevices = if (pagerState.currentPage == 0) switchDevices else otherDevices
    val activeCount = remember(currentPageDevices) { currentPageDevices.count { it.hasOnOff && it.isOn } }
    val controllableCount = remember(currentPageDevices) { currentPageDevices.count { it.hasOnOff } }
    val activeSwitchIds = remember(switchDevices) {
        switchDevices.filter { it.hasOnOff && it.isOn }.map { it.id }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Column {
                        Text(
                            text = if (pagerState.currentPage == 0) "Выключатели" else "Остальное",
                            fontWeight = FontWeight.Bold,
                            fontSize = 21.sp,
                            maxLines = 1
                        )
                        if (home != null) {
                            val subText = if (controllableCount > 0) {
                                "$activeCount из $controllableCount вкл."
                            } else {
                                "${currentPageDevices.size} устройств"
                            }
                            Text(
                                text = subText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Turn Off All Switches button on Switches screen
                    if (pagerState.currentPage == 0 && activeSwitchIds.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val prevSnapshot = snapshot
                                val idsToOff = activeSwitchIds
                                val updated = prevSnapshot?.copy(
                                    devices = prevSnapshot.devices.map { item ->
                                        if (idsToOff.contains(item.id)) item.copy(isOn = false) else item
                                    }
                                )
                                snapshot = updated
                                syncWidgetsAndTiles(updated)
                                errorMsg = null
                                scope.launch {
                                    apiClient.turnOffDevices(token!!, idsToOff)
                                        .onFailure { err ->
                                            snapshot = prevSnapshot
                                            syncWidgetsAndTiles(prevSnapshot)
                                            errorMsg = err.message
                                        }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Rounded.PowerSettingsNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Выкл всё", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showManageSheet = true
                        }
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Настройка плиток и шторки")
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            refresh()
                        }
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = if (isDark) Color(0xFF121620) else Color.White,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch { pagerState.animateScrollToPage(0) }
                    },
                    icon = { Icon(Icons.Rounded.ToggleOn, contentDescription = "Выключатели") },
                    label = {
                        Text(
                            text = if (home != null) "Выключатели (${switchDevices.size})" else "Выключатели",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    icon = { Icon(Icons.Rounded.Widgets, contentDescription = "Остальное") },
                    label = {
                        Text(
                            text = if (home != null) "Остальное (${otherDevices.size})" else "Остальное",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (isRefreshing && home == null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (errorMsg != null) {
                Surface(
                    color = Color(0xFFDC2626).copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Text(
                        text = errorMsg!!,
                        color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Two-screen pager with Pull-to-Refresh on each page
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                val pageDevices = if (page == 0) switchDevices else otherDevices

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        refresh()
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (home != null && pageDevices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (page == 0) Icons.Rounded.ToggleOn else Icons.Rounded.Widgets,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(42.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = if (page == 0) "Выключателей пока нет" else "Других устройств пока нет",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 156.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(pageDevices, key = { it.id }) { dev ->
                                DeviceTile(
                                    device = dev,
                                    roomName = dev.roomId?.let { roomMap[it]?.name },
                                    isDark = isDark,
                                    isFavorite = favoriteIds.contains(dev.id),
                                    isHidden = hiddenIds.contains(dev.id),
                                    onTap = {
                                        if (dev.hasOnOff) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val nextState = !dev.isOn
                                            val prevSnapshot = snapshot
                                            val updated = prevSnapshot?.copy(
                                                devices = prevSnapshot.devices.map { item ->
                                                    if (item.id == dev.id) item.copy(isOn = nextState) else item
                                                }
                                            )
                                            snapshot = updated
                                            syncWidgetsAndTiles(updated)
                                            errorMsg = null
                                            scope.launch {
                                                apiClient.togglePower(token!!, dev.id, nextState)
                                                    .onFailure { err ->
                                                        snapshot = prevSnapshot
                                                        syncWidgetsAndTiles(prevSnapshot)
                                                        errorMsg = err.message
                                                    }
                                            }
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            refresh()
                                        }
                                    },
                                    onLongTap = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedDeviceForMenu = dev
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Long-press Device Management Sheet: Favorite, Sort Up/Down, Widget, Quick Settings, Hide, Brightness
    selectedDeviceForMenu?.let { dev ->
        val devRoom = dev.roomId?.let { roomMap[it]?.name }
        val isFav = remember(dev.id, prefsVersion) { prefsStore.isFavorite(dev.id) }
        val isHid = remember(dev.id, prefsVersion) { prefsStore.isHidden(dev.id) }
        val inWidget = remember(dev.id, prefsVersion) { prefsStore.getWidgetDeviceIds().contains(dev.id) }
        val isQs1 = remember(dev.id, prefsVersion) { prefsStore.getQuickTileDeviceId(1) == dev.id }
        val isQs2 = remember(dev.id, prefsVersion) { prefsStore.getQuickTileDeviceId(2) == dev.id }

        ModalBottomSheet(
            onDismissRequest = { selectedDeviceForMenu = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = listOfNotNull(devRoom, dev.name).joinToString(" · "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (dev.brightness != null) {
                    var sliderVal by remember(dev.id) { mutableFloatStateOf((dev.brightness ?: 100).toFloat()) }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Яркость: ${sliderVal.toInt()}%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Slider(
                                value = sliderVal,
                                onValueChange = { sliderVal = it },
                                onValueChangeFinished = {
                                    val b = sliderVal.toInt()
                                    val updated = snapshot?.copy(
                                        devices = snapshot!!.devices.map { item ->
                                            if (item.id == dev.id) item.copy(brightness = b, isOn = true) else item
                                        }
                                    )
                                    snapshot = updated
                                    syncWidgetsAndTiles(updated)
                                    scope.launch {
                                        apiClient.setBrightness(token!!, dev.id, b)
                                            .onFailure { errorMsg = it.message }
                                    }
                                },
                                valueRange = 1f..100f
                            )
                        }
                    }
                }

                // Favorite & Hide row
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = {
                            prefsStore.toggleFavorite(dev.id)
                            prefsVersion++
                            syncWidgetsAndTiles(snapshot)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isFav) "В избранном" else "В избранное", maxLines = 1, fontSize = 13.sp)
                    }

                    FilledTonalButton(
                        onClick = {
                            prefsStore.toggleHidden(dev.id)
                            prefsVersion++
                            syncWidgetsAndTiles(snapshot)
                            selectedDeviceForMenu = null
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = if (isHid) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isHid) "Вернуть" else "Скрыть", maxLines = 1, fontSize = 13.sp)
                    }
                }

                // Move Up / Down row
                val currentPool = if (dev.type.startsWith("devices.types.switch")) switchDevices else otherDevices
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            prefsStore.moveDevice(dev.id, currentPool.map { it.id }, -1)
                            prefsVersion++
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Выше", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            prefsStore.moveDevice(dev.id, currentPool.map { it.id }, 1)
                            prefsVersion++
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ниже", fontSize = 13.sp)
                    }
                }

                // Widget & Quick Settings tile bindings
                if (dev.hasOnOff) {
                    FilledTonalButton(
                        onClick = {
                            prefsStore.toggleWidgetDevice(dev.id)
                            prefsVersion++
                            syncWidgetsAndTiles(snapshot)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Widgets, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (inWidget) "Убрать с виджета рабочего стола" else "Добавить на виджет рабочего стола",
                            fontSize = 13.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                prefsStore.setQuickTileDeviceId(1, if (isQs1) null else dev.id)
                                prefsVersion++
                                syncWidgetsAndTiles(snapshot)
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isQs1) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text("Шторка #1", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                prefsStore.setQuickTileDeviceId(2, if (isQs2) null else dev.id)
                                prefsVersion++
                                syncWidgetsAndTiles(snapshot)
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isQs2) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text("Шторка #2", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Manage Hidden, Widget, Quick Settings Tiles & Logout Sheet
    if (showManageSheet) {
        val qs1Id = remember(prefsVersion) { prefsStore.getQuickTileDeviceId(1) }
        val qs2Id = remember(prefsVersion) { prefsStore.getQuickTileDeviceId(2) }
        val widgetIds = remember(prefsVersion) { prefsStore.getWidgetDeviceIds() }
        val hiddenDevices = remember(allDevices, hiddenIds) { allDevices.filter { hiddenIds.contains(it.id) } }

        ModalBottomSheet(
            onDismissRequest = { showManageSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Настройки и виджеты",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Показывать скрытые", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "Скрыто устройств: ${hiddenIds.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = showHidden, onCheckedChange = { showHidden = it })
                        }
                    }
                }

                if (hiddenDevices.isNotEmpty()) {
                    item {
                        OutlinedButton(
                            onClick = {
                                prefsStore.unhideAll()
                                showHidden = false
                                prefsVersion++
                                syncWidgetsAndTiles(snapshot)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Показать все скрытые устройства")
                        }
                    }
                }

                item {
                    val qs1Name = allDevices.find { it.id == qs1Id }?.let { d ->
                        listOfNotNull(d.roomId?.let { roomMap[it]?.name }, d.name).joinToString(" · ")
                    } ?: "Авто (первый выключатель)"
                    val qs2Name = allDevices.find { it.id == qs2Id }?.let { d ->
                        listOfNotNull(d.roomId?.let { roomMap[it]?.name }, d.name).joinToString(" · ")
                    } ?: "Авто (второй выключатель)"

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Быстрые плитки в шторке Android", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Плитка #1: $qs1Name", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Плитка #2: $qs2Name", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "Долгое нажатие на любой плитке позволяет закрепить её в избранном, скрыть, сдвинуть или назначить в шторку и на виджет.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Виджет на рабочем столе", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                if (widgetIds.isEmpty()) "Сейчас: Авто (избранные или первые 4 выключателя)"
                                else "Выбрано устройств на виджете: ${widgetIds.size} из 4",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { syncWidgetsAndTiles(snapshot) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Обновить виджеты", fontSize = 12.sp)
                                }
                                if (widgetIds.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            prefsStore.clearWidgetDevices()
                                            prefsVersion++
                                            syncWidgetsAndTiles(snapshot)
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Сброс", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            showManageSheet = false
                            tokenStore.clear()
                            token = null
                            snapshot = null
                            syncWidgetsAndTiles(null)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Выйти из аккаунта Яндекса", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceTile(
    device: SmartDevice,
    roomName: String?,
    isDark: Boolean,
    isFavorite: Boolean,
    isHidden: Boolean,
    onTap: () -> Unit,
    onLongTap: () -> Unit
) {
    val category = classifyDevice(device.type)
    val isActive = device.hasOnOff && device.isOn

    val gradientColors: List<Color> = when {
        isActive && category == DeviceCategory.LIGHT -> listOf(Color(0xFFFFCA3A), Color(0xFFF59E0B))
        isActive && category == DeviceCategory.CLIMATE -> listOf(Color(0xFF0EA5E9), Color(0xFF0369A1))
        isActive && category == DeviceCategory.POWER -> listOf(Color(0xFF10B981), Color(0xFF059669))
        isActive -> listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
        isDark -> listOf(Color(0xFF161C28), Color(0xFF131823))
        else -> listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC))
    }

    val titleColor by animateColorAsState(
        targetValue = when {
            isActive && category == DeviceCategory.LIGHT -> Color(0xFF0F172A)
            isActive -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(180),
        label = "titleColor"
    )

    val subtitleColor by animateColorAsState(
        targetValue = when {
            isActive && category == DeviceCategory.LIGHT -> Color(0xFF332100)
            isActive -> Color.White.copy(alpha = 0.88f)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(180),
        label = "subtitleColor"
    )

    val inactiveAccent = when (category) {
        DeviceCategory.LIGHT -> Color(0xFFF59E0B)
        DeviceCategory.CLIMATE -> Color(0xFF0EA5E9)
        DeviceCategory.POWER -> Color(0xFF10B981)
        DeviceCategory.SENSOR -> Color(0xFF06B6D4)
        else -> Color(0xFF3B82F6)
    }

    val iconBgColor = when {
        isActive && category == DeviceCategory.LIGHT -> Color(0xFF0F172A).copy(alpha = 0.14f)
        isActive -> Color.White.copy(alpha = 0.22f)
        else -> inactiveAccent.copy(alpha = if (isDark) 0.18f else 0.13f)
    }

    val iconTint = when {
        isActive && category == DeviceCategory.LIGHT -> Color(0xFF0F172A)
        isActive -> Color.White
        else -> inactiveAccent
    }

    val borderStroke = when {
        isActive && category == DeviceCategory.LIGHT -> BorderStroke(1.5.dp, Color(0xFFFBBF24))
        isActive -> BorderStroke(1.5.dp, Color.White.copy(alpha = 0.22f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        border = borderStroke,
        shadowElevation = if (isActive) 6.dp else if (isDark) 0.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isHidden) 0.52f else 1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(146.dp)
                .background(Brush.linearGradient(gradientColors))
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongTap
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row: vibrant category icon + favorite star + state indicator text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(iconBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconForDevice(device.type),
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(23.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isFavorite) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "Избранное",
                                tint = if (isActive && category == DeviceCategory.LIGHT) Color(0xFF0F172A) else if (isActive) Color.White else Color(0xFFF59E0B),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        val badgeText = when {
                            device.brightness != null && isActive -> "${device.brightness}%"
                            device.temperatureTarget != null && isActive -> "${device.temperatureTarget}°"
                            device.primarySensorText != null -> device.primarySensorText
                            device.hasOnOff && isActive -> "ВКЛ"
                            device.hasOnOff -> "ВЫКЛ"
                            else -> ""
                        }
                        if (badgeText.isNotEmpty()) {
                            Text(
                                text = badgeText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) titleColor else inactiveAccent
                            )
                        }
                    }
                }

                // Bottom row: Bold Room + Status on top, Equipment name underneath
                Column {
                    val statusLabel = when {
                        device.brightness != null && isActive -> "${device.brightness}%"
                        device.temperatureTarget != null && isActive -> "${device.temperatureTarget}°"
                        device.hasOnOff && isActive -> "Включено"
                        device.hasOnOff -> "Выключено"
                        device.primarySensorText != null -> device.primarySensorText
                        else -> "Датчик"
                    }
                    val primaryLine = listOfNotNull(roomName, statusLabel).joinToString(" · ")
                    Text(
                        text = primaryLine,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (isHidden) "${device.name} · скрыто" else device.name,
                        fontSize = 12.sp,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    initialClientId: String,
    isLoading: Boolean,
    errorMsg: String?,
    onOpenOAuth: (String) -> Unit,
    onSubmitToken: (String) -> Unit
) {
    var clientIdInput by remember { mutableStateOf(initialClientId) }
    var tokenInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Алиса Дом", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Подключите аккаунт Яндекса один раз — управление устройствами работает мгновенно и без лишних экранов.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(22.dp))

        if (errorMsg != null) {
            Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = clientIdInput,
            onValueChange = { clientIdInput = it },
            label = { Text("Client ID из oauth.yandex.ru") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val cid = clientIdInput.trim()
                if (cid.isNotEmpty()) onOpenOAuth(cid)
            },
            enabled = clientIdInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Получить OAuth-токен в Яндексе", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = { Text("OAuth-токен Яндекса") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onSubmitToken(tokenInput.trim()) },
            enabled = !isLoading && tokenInput.trim().length >= 16,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (isLoading) "Проверка..." else "Подключить дом", fontWeight = FontWeight.Bold)
        }
    }
}
