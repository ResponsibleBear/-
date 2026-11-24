package ru.alicedom.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
                    background = Color(0xFF0B0F17),
                    surface = Color(0xFF151C29),
                    surfaceVariant = Color(0xFF1F283A),
                    onBackground = Color(0xFFF3F6FC),
                    onSurface = Color(0xFFF3F6FC),
                    onSurfaceVariant = Color(0xFF9AA5BA),
                    outline = Color(0xFF2B364C)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF2563EB),
                    onPrimary = Color.White,
                    background = Color(0xFFE6EBF3),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFDCE3EF),
                    onBackground = Color(0xFF0F172A),
                    onSurface = Color(0xFF0F172A),
                    onSurfaceVariant = Color(0xFF526077),
                    outline = Color(0xFFC8D2E1)
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
    var selectedPairForMenu by remember { mutableStateOf<Pair<SmartDevice, SmartDevice>?>(null) }
    var showPairPicker by remember { mutableStateOf(false) }
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
    val switchGridItems = remember(switchDevices, prefsVersion) {
        prefsStore.groupDoubleSwitches(switchDevices)
    }
    val otherDevices = remember(allDevices, prefsVersion, showHidden) {
        prefsStore.sortAndFilter(
            devices = allDevices.filter { !it.type.startsWith("devices.types.switch") },
            showHidden = showHidden
        )
    }

    fun toggleSingleDevice(dev: SmartDevice) {
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
    }

    val activeSwitchIds = remember(switchDevices) {
        switchDevices.filter { it.hasOnOff && it.isOn }.map { it.id }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pagerState.currentPage == 0) {
                        Tactile3DActionButton(
                            label = "Выключить все",
                            icon = Icons.Rounded.PowerSettingsNew,
                            tone = if (activeSwitchIds.isNotEmpty()) TactileButtonTone.PRIMARY else TactileButtonTone.NEUTRAL,
                            isDark = isDark,
                            enabled = switchDevices.any { it.hasOnOff },
                            totalHeight = 56.dp,
                            lipDepth = 9.dp,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val idsToOff = activeSwitchIds.ifEmpty {
                                    switchDevices.filter { it.hasOnOff }.map { it.id }
                                }
                                if (idsToOff.isEmpty()) return@Tactile3DActionButton
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val prevSnapshot = snapshot
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
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "Остальное",
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Tactile3DIconButton(
                        icon = Icons.Rounded.Tune,
                        contentDescription = "Настройка плиток и шторки",
                        isDark = isDark,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showManageSheet = true
                        }
                    )
                    Tactile3DIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Обновить",
                        isDark = isDark,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            refresh()
                        }
                    )
                }

                // Minimal 4dp swipe page indicator (replaces bottom navigation bar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(2) { pageIdx ->
                        val active = pagerState.currentPage == pageIdx
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .width(if (active) 22.dp else 7.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                                )
                        )
                    }
                }
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
                    } else if (page == 0) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 156.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = switchGridItems,
                                key = { it.stableKey },
                                span = { item ->
                                    GridItemSpan(if (item is SwitchGridItem.Double) minOf(2, maxLineSpan) else 1)
                                }
                            ) { item ->
                                when (item) {
                                    is SwitchGridItem.Double -> {
                                        DoubleSwitchTile(
                                            left = item.left,
                                            right = item.right,
                                            commonTitle = item.commonTitle,
                                            roomName = item.left.roomId?.let { roomMap[it]?.name },
                                            isDark = isDark,
                                            leftFavorite = favoriteIds.contains(item.left.id),
                                            rightFavorite = favoriteIds.contains(item.right.id),
                                            onTapKey = { keyDev -> toggleSingleDevice(keyDev) },
                                            onLongTapKey = { keyDev ->
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                selectedDeviceForMenu = keyDev
                                                selectedPairForMenu = item.left to item.right
                                                showPairPicker = false
                                            }
                                        )
                                    }
                                    is SwitchGridItem.Single -> {
                                        val dev = item.device
                                        DeviceTile(
                                            device = dev,
                                            roomName = dev.roomId?.let { roomMap[it]?.name },
                                            isDark = isDark,
                                            isFavorite = favoriteIds.contains(dev.id),
                                            isHidden = hiddenIds.contains(dev.id),
                                            onTap = { toggleSingleDevice(dev) },
                                            onLongTap = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                selectedDeviceForMenu = dev
                                                selectedPairForMenu = null
                                                showPairPicker = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 156.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(pageDevices, key = { it.id }) { dev ->
                                DeviceTile(
                                    device = dev,
                                    roomName = dev.roomId?.let { roomMap[it]?.name },
                                    isDark = isDark,
                                    isFavorite = favoriteIds.contains(dev.id),
                                    isHidden = hiddenIds.contains(dev.id),
                                    onTap = { toggleSingleDevice(dev) },
                                    onLongTap = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedDeviceForMenu = dev
                                        selectedPairForMenu = null
                                        showPairPicker = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Long-press Device Management Sheet: Pair/Split 2-gang, Favorite, Sort Up/Down, Widget, Quick Settings, Hide, Brightness
    selectedDeviceForMenu?.let { dev ->
        val devRoom = dev.roomId?.let { roomMap[it]?.name }
        val isFav = remember(dev.id, prefsVersion) { prefsStore.isFavorite(dev.id) }
        val isHid = remember(dev.id, prefsVersion) { prefsStore.isHidden(dev.id) }
        val inWidget = remember(dev.id, prefsVersion) { prefsStore.getWidgetDeviceIds().contains(dev.id) }
        val isQs1 = remember(dev.id, prefsVersion) { prefsStore.getQuickTileDeviceId(1) == dev.id }
        val isQs2 = remember(dev.id, prefsVersion) { prefsStore.getQuickTileDeviceId(2) == dev.id }
        val activePair = selectedPairForMenu

        ModalBottomSheet(
            onDismissRequest = {
                selectedDeviceForMenu = null
                selectedPairForMenu = null
                showPairPicker = false
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = listOfNotNull(devRoom, dev.name).joinToString(" · "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Double-switch Pair / Split actions
                if (activePair != null) {
                    Tactile3DActionButton(
                        label = "Разъединить клавиши на 2 плитки",
                        icon = Icons.Rounded.LinkOff,
                        tone = TactileButtonTone.NEUTRAL,
                        isDark = isDark,
                        totalHeight = 50.dp,
                        lipDepth = 7.dp,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            prefsStore.splitSwitchPair(activePair.first.id, activePair.second.id)
                            prefsVersion++
                            selectedPairForMenu = null
                            selectedDeviceForMenu = null
                        }
                    )
                } else if (dev.type.startsWith("devices.types.switch") && switchDevices.size >= 2) {
                    val partnerCandidates = remember(dev.id, switchDevices) {
                        switchDevices
                            .filter { it.id != dev.id }
                            .sortedBy { if (it.roomId == dev.roomId) 0 else 1 }
                    }
                    Tactile3DActionButton(
                        label = if (showPairPicker) "Скрыть выбор второго выключателя" else "Объединить в двухклавишный",
                        icon = Icons.Rounded.ViewColumn,
                        tone = if (showPairPicker) TactileButtonTone.PRIMARY else TactileButtonTone.NEUTRAL,
                        isDark = isDark,
                        totalHeight = 50.dp,
                        lipDepth = 7.dp,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showPairPicker = !showPairPicker }
                    )

                    if (showPairPicker) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Выберите второй выключатель для левой/правой клавиши:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                partnerCandidates.take(8).forEach { cand ->
                                    val candRoom = cand.roomId?.let { roomMap[it]?.name }
                                    Tactile3DActionButton(
                                        label = listOfNotNull(candRoom, cand.name).joinToString(" · "),
                                        tone = TactileButtonTone.NEUTRAL,
                                        isDark = isDark,
                                        totalHeight = 46.dp,
                                        lipDepth = 6.dp,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            prefsStore.addManualSwitchPair(dev.id, cand.id)
                                            prefsVersion++
                                            showPairPicker = false
                                            selectedDeviceForMenu = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

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
                    Tactile3DActionButton(
                        label = if (isFav) "В избранном" else "В избранное",
                        icon = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        tone = if (isFav) TactileButtonTone.PRIMARY else TactileButtonTone.NEUTRAL,
                        isDark = isDark,
                        totalHeight = 50.dp,
                        lipDepth = 7.dp,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            prefsStore.toggleFavorite(dev.id)
                            prefsVersion++
                            syncWidgetsAndTiles(snapshot)
                        }
                    )

                    Tactile3DActionButton(
                        label = if (isHid) "Вернуть" else "Скрыть",
                        icon = if (isHid) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        tone = TactileButtonTone.NEUTRAL,
                        isDark = isDark,
                        totalHeight = 50.dp,
                        lipDepth = 7.dp,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            prefsStore.toggleHidden(dev.id)
                            prefsVersion++
                            syncWidgetsAndTiles(snapshot)
                            selectedDeviceForMenu = null
                            selectedPairForMenu = null
                        }
                    )
                }

                // Move Up / Down row
                val currentPool = if (dev.type.startsWith("devices.types.switch")) switchDevices else otherDevices
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Tactile3DActionButton(
                        label = "Выше",
                        icon = Icons.Rounded.ArrowUpward,
                        tone = TactileButtonTone.NEUTRAL,
                        isDark = isDark,
                        totalHeight = 48.dp,
                        lipDepth = 7.dp,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            prefsStore.moveDevice(dev.id, currentPool.map { it.id }, -1)
                            prefsVersion++
                        }
                    )
                    Tactile3DActionButton(
                        label = "Ниже",
                        icon = Icons.Rounded.ArrowDownward,
                        tone = TactileButtonTone.NEUTRAL,
                        isDark = isDark,
                        totalHeight = 48.dp,
                        lipDepth = 7.dp,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            prefsStore.moveDevice(dev.id, currentPool.map { it.id }, 1)
                            prefsVersion++
                        }
                    )
                }

                // Widget & Quick Settings tile bindings
                if (dev.hasOnOff) {
                    Tactile3DActionButton(
                        label = if (inWidget) "Убрать с виджета" else "На виджет рабочего стола",
                        icon = Icons.Rounded.Widgets,
                        tone = if (inWidget) TactileButtonTone.PRIMARY else TactileButtonTone.NEUTRAL,
                        isDark = isDark,
                        totalHeight = 50.dp,
                        lipDepth = 7.dp,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            prefsStore.toggleWidgetDevice(dev.id)
                            prefsVersion++
                            syncWidgetsAndTiles(snapshot)
                        }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Tactile3DActionButton(
                            label = "Шторка #1",
                            icon = if (isQs1) Icons.Rounded.Check else null,
                            tone = if (isQs1) TactileButtonTone.PRIMARY else TactileButtonTone.NEUTRAL,
                            isDark = isDark,
                            totalHeight = 48.dp,
                            lipDepth = 7.dp,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                prefsStore.setQuickTileDeviceId(1, if (isQs1) null else dev.id)
                                prefsVersion++
                                syncWidgetsAndTiles(snapshot)
                            }
                        )
                        Tactile3DActionButton(
                            label = "Шторка #2",
                            icon = if (isQs2) Icons.Rounded.Check else null,
                            tone = if (isQs2) TactileButtonTone.PRIMARY else TactileButtonTone.NEUTRAL,
                            isDark = isDark,
                            totalHeight = 48.dp,
                            lipDepth = 7.dp,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                prefsStore.setQuickTileDeviceId(2, if (isQs2) null else dev.id)
                                prefsVersion++
                                syncWidgetsAndTiles(snapshot)
                            }
                        )
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
                        Tactile3DActionButton(
                            label = "Показать все скрытые устройства",
                            tone = TactileButtonTone.NEUTRAL,
                            isDark = isDark,
                            totalHeight = 50.dp,
                            lipDepth = 7.dp,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                prefsStore.unhideAll()
                                showHidden = false
                                prefsVersion++
                                syncWidgetsAndTiles(snapshot)
                            }
                        )
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
                        shape = RoundedCornerShape(18.dp),
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
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Виджет на рабочем столе", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                if (widgetIds.isEmpty()) "Сейчас: Авто (избранные или первые 4 выключателя)"
                                else "Выбрано устройств на виджете: ${widgetIds.size} из 4",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Tactile3DActionButton(
                                    label = "Обновить виджеты",
                                    tone = TactileButtonTone.NEUTRAL,
                                    isDark = isDark,
                                    totalHeight = 48.dp,
                                    lipDepth = 7.dp,
                                    modifier = Modifier.weight(1f),
                                    onClick = { syncWidgetsAndTiles(snapshot) }
                                )
                                if (widgetIds.isNotEmpty()) {
                                    Tactile3DActionButton(
                                        label = "Сброс",
                                        tone = TactileButtonTone.NEUTRAL,
                                        isDark = isDark,
                                        fillWidth = false,
                                        totalHeight = 48.dp,
                                        lipDepth = 7.dp,
                                        onClick = {
                                            prefsStore.clearWidgetDevices()
                                            prefsVersion++
                                            syncWidgetsAndTiles(snapshot)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Tactile3DActionButton(
                        label = "Выйти из аккаунта Яндекса",
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        tone = TactileButtonTone.DANGER,
                        isDark = isDark,
                        totalHeight = 52.dp,
                        lipDepth = 8.dp,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showManageSheet = false
                            tokenStore.clear()
                            token = null
                            snapshot = null
                            syncWidgetsAndTiles(null)
                        }
                    )
                }
            }
        }
    }
}

private enum class TactileButtonTone { NEUTRAL, PRIMARY, DANGER }

@Composable
private fun Tactile3DActionButton(
    label: String,
    icon: ImageVector? = null,
    tone: TactileButtonTone = TactileButtonTone.NEUTRAL,
    isDark: Boolean,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
    modifier: Modifier = Modifier,
    totalHeight: Dp = 56.dp,
    lipDepth: Dp = 9.dp,
    cornerRadius: Dp = 18.dp,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val maxTravel = (lipDepth - 2.dp).coerceAtLeast(3.dp)
    val offsetY by animateDpAsState(
        targetValue = if (pressed && enabled) maxTravel else 0.dp,
        animationSpec = tween(90),
        label = "btnPress"
    )
    val faceHeight = totalHeight - lipDepth
    val shape = RoundedCornerShape(cornerRadius)

    val lipBrush = when (tone) {
        TactileButtonTone.PRIMARY -> Brush.verticalGradient(listOf(Color(0xFF1E40AF), Color(0xFF172554)))
        TactileButtonTone.DANGER -> Brush.verticalGradient(listOf(Color(0xFFB91C1C), Color(0xFF7F1D1D)))
        TactileButtonTone.NEUTRAL -> if (isDark) {
            Brush.verticalGradient(listOf(Color(0xFF0A0E17), Color(0xFF04060B)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFF94A3B8), Color(0xFF7B8CA4)))
        }
    }
    val lipBorderColor = when (tone) {
        TactileButtonTone.PRIMARY -> Color(0xFF172554)
        TactileButtonTone.DANGER -> Color(0xFF7F1D1D)
        TactileButtonTone.NEUTRAL -> if (isDark) Color(0xFF334155).copy(alpha = 0.65f) else Color(0xFF64748B).copy(alpha = 0.65f)
    }
    val shadowColor = when (tone) {
        TactileButtonTone.PRIMARY -> Color(0xFF1E3A8A)
        TactileButtonTone.DANGER -> Color(0xFF991B1B)
        TactileButtonTone.NEUTRAL -> Color.Black
    }
    val faceBrush = when (tone) {
        TactileButtonTone.PRIMARY -> Brush.verticalGradient(listOf(Color(0xFF60A5FA), Color(0xFF3B82F6), Color(0xFF1D4ED8)))
        TactileButtonTone.DANGER -> Brush.verticalGradient(listOf(Color(0xFFF87171), Color(0xFFEF4444), Color(0xFFDC2626)))
        TactileButtonTone.NEUTRAL -> if (isDark) {
            Brush.verticalGradient(listOf(Color(0xFF2B364D), Color(0xFF1E2638), Color(0xFF141B29)))
        } else {
            Brush.verticalGradient(listOf(Color.White, Color(0xFFF3F6FB), Color(0xFFE1E8F3)))
        }
    }
    val faceBorderBrush = when (tone) {
        TactileButtonTone.PRIMARY -> Brush.verticalGradient(listOf(Color(0xFFDBEAFE).copy(alpha = 0.85f), Color(0xFF1E3A8A)))
        TactileButtonTone.DANGER -> Brush.verticalGradient(listOf(Color(0xFFFEE2E2).copy(alpha = 0.7f), Color(0xFF991B1B)))
        TactileButtonTone.NEUTRAL -> if (isDark) {
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.24f), Color(0xFF0A0E17)))
        } else {
            Brush.verticalGradient(listOf(Color.White, Color(0xFF94A3B8)))
        }
    }
    val contentColor = when {
        tone == TactileButtonTone.PRIMARY || tone == TactileButtonTone.DANGER -> Color.White
        isDark -> Color(0xFFF3F6FC)
        else -> Color(0xFF0F172A)
    }

    Box(
        modifier = modifier
            .height(totalHeight)
            .alpha(if (enabled) 1f else 0.55f)
    ) {
        Box(
            modifier = (if (fillWidth) Modifier.fillMaxSize() else Modifier.matchParentSize())
                .padding(top = lipDepth)
                .shadow(
                    elevation = if (tone != TactileButtonTone.NEUTRAL) 10.dp else if (isDark) 8.dp else 6.dp,
                    shape = shape,
                    ambientColor = shadowColor,
                    spotColor = shadowColor
                )
                .clip(shape)
                .background(lipBrush)
                .border(BorderStroke(1.dp, lipBorderColor), shape)
        )
        Box(
            modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .height(faceHeight)
                .offset(y = offsetY)
                .clip(shape)
                .background(faceBrush)
                .border(BorderStroke(1.4.dp, faceBorderBrush), shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Tactile3DIconButton(
    icon: ImageVector,
    contentDescription: String,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val offsetY by animateDpAsState(if (pressed) 6.dp else 0.dp, tween(90), label = "iconPress")
    val lipBrush = if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF0A0E17), Color(0xFF04060B)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF94A3B8), Color(0xFF7B8CA4)))
    }
    val lipBorderColor = if (isDark) Color(0xFF334155).copy(alpha = 0.65f) else Color(0xFF64748B).copy(alpha = 0.65f)
    val faceBrush = if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF2B364D), Color(0xFF1E2638), Color(0xFF141B29)))
    } else {
        Brush.verticalGradient(listOf(Color.White, Color(0xFFF3F6FB), Color(0xFFE1E8F3)))
    }
    val borderBrush = if (isDark) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.24f), Color(0xFF0A0E17)))
    } else {
        Brush.verticalGradient(listOf(Color.White, Color(0xFF94A3B8)))
    }
    val tint = if (isDark) Color(0xFFF3F6FC) else Color(0xFF0F172A)
    val shape = RoundedCornerShape(18.dp)

    Box(modifier = Modifier.size(width = 52.dp, height = 56.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 9.dp)
                .shadow(
                    elevation = if (isDark) 8.dp else 6.dp,
                    shape = shape,
                    ambientColor = Color.Black,
                    spotColor = Color.Black
                )
                .clip(shape)
                .background(lipBrush)
                .border(BorderStroke(1.dp, lipBorderColor), shape)
        )
        Box(
            modifier = Modifier
                .offset(y = offsetY)
                .fillMaxWidth()
                .height(47.dp)
                .clip(shape)
                .background(faceBrush)
                .border(BorderStroke(1.4.dp, borderBrush), shape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
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
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()

    val keycapY by animateDpAsState(
        targetValue = when {
            isPressed -> 5.dp
            isActive -> 2.dp
            else -> 0.dp
        },
        animationSpec = tween(100),
        label = "tilePress"
    )

    val gradientColors: List<Color> = when {
        isActive && category == DeviceCategory.LIGHT -> listOf(Color(0xFFFDE047), Color(0xFFFBBF24), Color(0xFFF59E0B))
        isActive && category == DeviceCategory.CLIMATE -> listOf(Color(0xFF38BDF8), Color(0xFF0EA5E9), Color(0xFF0284C7))
        isActive && category == DeviceCategory.POWER -> listOf(Color(0xFF34D399), Color(0xFF10B981), Color(0xFF059669))
        isActive -> listOf(Color(0xFF60A5FA), Color(0xFF3B82F6), Color(0xFF1D4ED8))
        isDark -> listOf(Color(0xFF2B364D), Color(0xFF1E2638), Color(0xFF141B29))
        else -> listOf(Color(0xFFFFFFFF), Color(0xFFF3F6FB), Color(0xFFE1E8F3))
    }

    val lipColor = when {
        isActive && category == DeviceCategory.LIGHT -> Color(0xFFB45309)
        isActive && category == DeviceCategory.CLIMATE -> Color(0xFF0369A1)
        isActive && category == DeviceCategory.POWER -> Color(0xFF047857)
        isActive -> Color(0xFF1E3A8A)
        isDark -> Color(0xFF070A11)
        else -> Color(0xFF8B9BB2)
    }

    val lipBorderColor = when {
        isActive -> lipColor
        isDark -> Color(0xFF334155).copy(alpha = 0.65f)
        else -> Color(0xFF64748B).copy(alpha = 0.65f)
    }

    val titleColor by animateColorAsState(
        targetValue = when {
            isActive && category == DeviceCategory.LIGHT -> Color(0xFF0F172A)
            isActive -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(160),
        label = "titleColor"
    )

    val subtitleColor by animateColorAsState(
        targetValue = when {
            isActive && category == DeviceCategory.LIGHT -> Color(0xFF332100)
            isActive -> Color.White.copy(alpha = 0.88f)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(160),
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

    val topBorderBrush = when {
        isActive && category == DeviceCategory.LIGHT -> Brush.verticalGradient(listOf(Color(0xFFFEF08A), Color(0xFFD97706)))
        isActive -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.52f), Color.Black.copy(alpha = 0.2f)))
        isDark -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.24f), Color(0xFF0A0E17)))
        else -> Brush.verticalGradient(listOf(Color.White, Color(0xFF94A3B8)))
    }

    val ledColor = when {
        isActive && category == DeviceCategory.LIGHT -> Color(0xFF0F172A).copy(alpha = 0.8f)
        isActive -> Color.White
        isDark -> Color(0xFF2D3950)
        else -> Color(0xFFCBD5E1)
    }

    val shape = RoundedCornerShape(24.dp)

    // 3D Keycap container with extruded bottom lip
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(154.dp)
            .alpha(if (isHidden) 0.52f else 1f)
    ) {
        // Extruded 3D bottom skirt
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
                .shadow(
                    elevation = if (isActive) 10.dp else if (isDark) 8.dp else 6.dp,
                    shape = shape,
                    ambientColor = if (isActive) lipColor else Color.Black,
                    spotColor = if (isActive) lipColor else Color.Black
                )
                .clip(shape)
                .background(lipColor)
                .border(BorderStroke(1.dp, lipBorderColor), shape)
        )

        // Raised / depressed 3D keycap face
        val isSwitch = device.type.startsWith("devices.types.switch")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(146.dp)
                .offset(y = keycapY)
                .clip(shape)
                .background(Brush.verticalGradient(gradientColors))
                .border(BorderStroke(1.4.dp, topBorderBrush), shape)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onTap,
                    onLongClick = onLongTap
                )
                .padding(16.dp)
        ) {
            if (isSwitch) {
                // Clean Switch Layout: Room + LED at top, large 2-line Name in middle, Status at bottom (no green toggle icon)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = roomName ?: "Выключатель",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = subtitleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isFavorite) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = "Избранное",
                                    tint = if (isActive) Color.White else Color(0xFFF59E0B),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(26.dp)
                                    .height(5.dp)
                                    .clip(CircleShape)
                                    .background(ledColor)
                            )
                        }
                    }

                    Text(
                        text = device.name,
                        fontSize = 17.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = when {
                            isHidden -> if (isActive) "Включено · скрыто" else "Выключено · скрыто"
                            isActive -> "Включено"
                            else -> "Выключено"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) Color.White.copy(alpha = 0.92f) else subtitleColor,
                        maxLines = 1
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top row: Sculpted 3D icon socket + Hardware LED bar & metric
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(iconBgColor)
                                .border(
                                    1.dp,
                                    if (isActive) Color.White.copy(alpha = 0.28f) else inactiveAccent.copy(alpha = 0.22f),
                                    RoundedCornerShape(15.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconForDevice(device.type),
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isFavorite) {
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = "Избранное",
                                        tint = if (isActive && category == DeviceCategory.LIGHT) Color(0xFF0F172A) else if (isActive) Color.White else Color(0xFFF59E0B),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(5.dp)
                                        .clip(CircleShape)
                                        .background(ledColor)
                                )
                            }
                            val metricText = when {
                                device.brightness != null && isActive -> "${device.brightness}%"
                                device.temperatureTarget != null && isActive -> "${device.temperatureTarget}°"
                                device.primarySensorText != null -> device.primarySensorText
                                else -> ""
                            }
                            if (metricText.isNotEmpty()) {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    text = metricText,
                                    fontSize = 12.sp,
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DoubleSwitchTile(
    left: SmartDevice,
    right: SmartDevice,
    commonTitle: String,
    roomName: String?,
    isDark: Boolean,
    leftFavorite: Boolean,
    rightFavorite: Boolean,
    onTapKey: (SmartDevice) -> Unit,
    onLongTapKey: (SmartDevice) -> Unit
) {
    val activeKeys = (if (left.isOn) 1 else 0) + (if (right.isOn) 1 else 0)
    val headerSummary = listOf(
        roomName ?: commonTitle,
        when (activeKeys) {
            2 -> "Обе клавиши вкл"
            1 -> "1 из 2 вкл"
            else -> "Выключено"
        }
    ).joinToString(" · ")

    val housingShape = RoundedCornerShape(26.dp)
    val housingLip = if (isDark) Color(0xFF05070B) else Color(0xFFB3BED0)
    val housingBrush = if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF0E131E), Color(0xFF131A28)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFD5DDEA), Color(0xFFDFE6F2)))
    }
    val housingBorder = if (isDark) Color(0xFF283349) else Color(0xFFBDC8DA)

    // Outer 3D Wall-Switch Recessed Frame
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 5.dp)
                .clip(housingShape)
                .background(housingLip)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
                .clip(housingShape)
                .background(housingBrush)
                .border(1.dp, housingBorder, housingShape)
                .padding(12.dp)
        ) {
            // Plate Header: Bold Room · Status + common title underneath
            Column(modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 10.dp)) {
                Text(
                    text = headerSummary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (roomName != null) {
                    Text(
                        text = commonTitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Twin 3D Rocker Paddles Side-by-Side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DoubleSwitchPaddle(
                    device = left,
                    fallbackLabel = "Клавиша 1",
                    isDark = isDark,
                    isFavorite = leftFavorite,
                    modifier = Modifier.weight(1f),
                    onTap = { onTapKey(left) },
                    onLongTap = { onLongTapKey(left) }
                )
                DoubleSwitchPaddle(
                    device = right,
                    fallbackLabel = "Клавиша 2",
                    isDark = isDark,
                    isFavorite = rightFavorite,
                    modifier = Modifier.weight(1f),
                    onTap = { onTapKey(right) },
                    onLongTap = { onLongTapKey(right) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DoubleSwitchPaddle(
    device: SmartDevice,
    fallbackLabel: String,
    isDark: Boolean,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLongTap: () -> Unit
) {
    val isActive = device.hasOnOff && device.isOn
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val keycapY by animateDpAsState(
        targetValue = when {
            isPressed -> 4.dp
            isActive -> 2.dp
            else -> 0.dp
        },
        animationSpec = tween(100),
        label = "paddlePress"
    )

    val faceBrush = if (isActive) {
        Brush.verticalGradient(listOf(Color(0xFF34D399), Color(0xFF10B981), Color(0xFF059669)))
    } else if (isDark) {
        Brush.verticalGradient(listOf(Color(0xFF2B364D), Color(0xFF1E2638), Color(0xFF141B29)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF3F6FB), Color(0xFFE1E8F3)))
    }
    val lipColor = if (isActive) Color(0xFF047857) else if (isDark) Color(0xFF070A11) else Color(0xFF8B9BB2)
    val lipBorderColor = if (isActive) Color(0xFF047857) else if (isDark) Color(0xFF334155).copy(alpha = 0.65f) else Color(0xFF64748B).copy(alpha = 0.65f)
    val borderBrush = if (isActive) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.52f), Color.Black.copy(alpha = 0.18f)))
    } else if (isDark) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.24f), Color(0xFF0A0E17)))
    } else {
        Brush.verticalGradient(listOf(Color.White, Color(0xFF94A3B8)))
    }
    val paddleShape = RoundedCornerShape(18.dp)

    Box(modifier = modifier.height(118.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 7.dp)
                .shadow(
                    elevation = if (isActive) 8.dp else if (isDark) 6.dp else 4.dp,
                    shape = paddleShape,
                    ambientColor = if (isActive) lipColor else Color.Black,
                    spotColor = if (isActive) lipColor else Color.Black
                )
                .clip(paddleShape)
                .background(lipColor)
                .border(BorderStroke(1.dp, lipBorderColor), paddleShape)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(111.dp)
                .offset(y = keycapY)
                .clip(paddleShape)
                .background(faceBrush)
                .border(BorderStroke(1.4.dp, borderBrush), paddleShape)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onTap,
                    onLongClick = onLongTap
                )
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Hardware LED slit at top-right (clean, zero toggle-switch icons)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = "Избранное",
                            tint = if (isActive) Color.White else Color(0xFFF59E0B),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) Color.White
                                else if (isDark) Color(0xFF2D3950)
                                else Color(0xFFCBD5E1)
                            )
                    )
                }

                // Generous 2-line bold equipment label in center
                Text(
                    text = device.name.ifBlank { fallbackLabel },
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Clean status at bottom
                Text(
                    text = if (isActive) "Включено" else "Выключено",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
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
    val isDark = isSystemInDarkTheme()
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
            shape = RoundedCornerShape(18.dp)
        )
        Spacer(Modifier.height(10.dp))
        Tactile3DActionButton(
            label = "Получить OAuth-токен в Яндексе",
            tone = TactileButtonTone.NEUTRAL,
            isDark = isDark,
            enabled = clientIdInput.isNotBlank(),
            totalHeight = 52.dp,
            lipDepth = 8.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val cid = clientIdInput.trim()
                if (cid.isNotEmpty()) onOpenOAuth(cid)
            }
        )

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = { Text("OAuth-токен Яндекса") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        Spacer(Modifier.height(14.dp))
        Tactile3DActionButton(
            label = if (isLoading) "Проверка..." else "Подключить дом",
            tone = TactileButtonTone.PRIMARY,
            isDark = isDark,
            enabled = !isLoading && tokenInput.trim().length >= 16,
            totalHeight = 54.dp,
            lipDepth = 8.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onSubmitToken(tokenInput.trim()) }
        )
    }
}
