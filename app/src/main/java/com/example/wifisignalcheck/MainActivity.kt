package com.example.wifisignalcheck

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            PermissionWrapper()
        }
    }
}

@Composable
fun PermissionWrapper() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    if (hasPermission) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        SignalColorApp(wifiManager)
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Lütfən, yerləşmə icazəsini verin", color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalColorApp(wifiManager: WifiManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var ssid by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(0) }
    var linkSpeed by remember { mutableStateOf(0) }
    var isConnected by remember { mutableStateOf(false) }
    var externalIp by remember { mutableStateOf("Yüklənir...") }
    val animatedDbm = remember { Animatable(-100f) }

    var portInput by rememberSaveable { mutableStateOf("") }
    var portStatus by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var isPortChecking by remember { mutableStateOf(false) }

    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var showScanPopup by rememberSaveable { mutableStateOf(false) }
    var showDbmPopup by rememberSaveable { mutableStateOf(false) }
    var showFrequencyPopup by rememberSaveable { mutableStateOf(false) }
    var showIpPopup by rememberSaveable { mutableStateOf(false) }
    var showSpeedtestPopup by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var lastIpCheck = 0L
        while (true) {
            val info = wifiManager.connectionInfo
            val currentRssi = info.rssi

            if (currentRssi <= -127 || currentRssi == 0 || info.networkId == -1) {
                isConnected = false
                externalIp = "Bağlantı yoxdur"
            } else {
                isConnected = true
                ssid = info.ssid.replace("\"", "").let { if (it == "<unknown ssid>" || it.isEmpty()) "WiFi" else it }
                frequency = info.frequency
                linkSpeed = info.linkSpeed

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastIpCheck > 10000) {
                    launch(Dispatchers.IO) {
                        try {
                            val response = URL("https://api.ipify.org").readText()
                            externalIp = response.trim()
                        } catch (e: Exception) {
                            if (externalIp == "Yüklənir...") externalIp = "Xəta"
                        }
                    }
                    lastIpCheck = currentTime
                }
                animatedDbm.animateTo(currentRssi.toFloat(), tween(300, easing = LinearEasing))
            }
            delay(1000)
        }
    }

    val targetColor = if (isConnected) calculateSignalColor(animatedDbm.value.toInt()) else Color(0xDE, 0x06, 0x1A)
    val animatedBgColor by animateColorAsState(targetColor, tween(500), label = "Bg")

    Box(modifier = Modifier.fillMaxSize().background(animatedBgColor), contentAlignment = Alignment.Center) {
        if (!isConnected) {
            Text(
                text = "Cihazınız Wi-Fi şəbəkəsinə bağlı deyil",
                fontSize = 20.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 30.dp)
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "Xarici IP: $externalIp",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { showIpPopup = true }
                )

                Spacer(modifier = Modifier.height(25.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { showFrequencyPopup = true }
                ) {
                    Text(ssid, fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    val ghz = if (frequency > 4000) "5 GHz" else "2.4 GHz"
                    Text(
                        text = "$ghz — Kanal: ${convertFrequencyToChannel(frequency)} — $linkSpeed Mbps",
                        fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "${animatedDbm.value.toInt()} dBm",
                    fontSize = 84.sp, fontWeight = FontWeight.Black, color = Color.White,
                    modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { showDbmPopup = true }
                )

                Text("Təxmini məsafə: ${"%.1f".format(calculateDistance(animatedDbm.value.toInt(), frequency))} m", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(30.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row {
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    wifiManager.startScan()
                                    scanResults = wifiManager.scanResults.sortedByDescending { it.level }
                                    showScanPopup = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(50.dp).weight(1f)
                        ) {
                            Text("Kanalları analiz et", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val ip = getGatewayIp(context)
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://$ip"))) } catch (e: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(50.dp).weight(0.6f)
                        ) {
                            Text("Router", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Button(
                        onClick = { showSpeedtestPopup = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Speedtest", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        if (showSpeedtestPopup) SpeedtestDialog(onDismiss = { showSpeedtestPopup = false })

        if (showScanPopup) {
            AppDialog(onDismiss = { showScanPopup = false }) {
                Text("Kanal Analizatoru", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(10.dp))

                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    scanResults.forEach { result ->
                        val resSsid = result.SSID.ifEmpty { "Gizli şəbəkə" }
                        val chan = convertFrequencyToChannel(result.frequency)
                        val signal = result.level

                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(resSsid, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                                val band = if (result.frequency > 5925) "6 GHz" else if (result.frequency > 4900) "5 GHz" else "2.4 GHz"
                                Text("Kanal: $chan | $band", fontSize = 12.sp, color = Color.DarkGray)
                            }
                            Text("$signal dBm", fontWeight = FontWeight.Bold, color = if (signal > -60) Color(0xFF2E7D32) else if (signal > -80) Color(0xFFF9A825) else Color(0xFFD32F2F))
                        }
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                RecommendationBlock(frequency, scanResults)
            }
        }

        if (showIpPopup) {
            AppDialog(onDismiss = { showIpPopup = false }) {
                Text("Xarici IP ünvanı haqqında", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Xarici IP ünvan — internetdə Sizin şəbəkəni tanıdan unikal nömrədir. Saytlara daxil olanda və ya onlayn xidmətlərdən istifadə edəndə, Sizi internetdə məhz bu IP ünvanı ilə “görürlər”.", fontSize = 15.sp)

                if (externalIp.startsWith("185.146")) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFEBEE), RoundedCornerShape(8.dp)).padding(12.dp)) {
                        Text(text = buildAnnotatedString {
                            append("Sizin hazırki IP ünvanınız ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))) { append("NAT IP") }
                            append("-dir. Bu səbəbdən bəzi funksiyalar (server yaradılması, P2P qoşulmalar) məhdud ola bilər.\n\n«Ağ» IP üçün ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("127") }
                            append(" nömrəsinə müraciət edin.\n\n")
                            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("Qiymət: 5 AZN / ay") }
                        }, fontSize = 14.sp, color = Color(0xFFB71C1C))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color.LightGray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Port yoxlanışı", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = portInput,
                        onValueChange = { if (it.length <= 5) portInput = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("Port (məs. 443)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val p = portInput.toIntOrNull()
                            if (p != null) {
                                isPortChecking = true
                                scope.launch {
                                    portStatus = checkPort(externalIp, p)
                                    isPortChecking = false
                                }
                            }
                        },
                        enabled = !isPortChecking && portInput.isNotEmpty(),
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isPortChecking) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Yoxla")
                    }
                }
                if (portStatus != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (portStatus == true) "✅ Port AÇIQDIR" else "❌ Port BAĞLIDIR",
                        color = if (portStatus == true) Color(0xFF4CAF50) else Color(0xFFD32F2F),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (showDbmPopup) {
            AppDialog(onDismiss = { showDbmPopup = false }) {
                Text("Siqnal Səviyyəsi", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                InfoItem("🟢 -30 … -50 dBm — Əla", "Router yaxındadır, maksimal sürət, 4K video və oyunlar üçün idealdır.")
                InfoItem("🟢 -50 … -60 dBm — Çox yaxşı", "Stabil internet, iş və yayım (stream) üçün uyğundur.")
                InfoItem("🟡 -60 … -67 dBm — Normal", "İnternet stabil işləyir, lakin sürət maksimumdan aşağı ola bilər.")
                InfoItem("🟡 -67 … -70 dBm — Limit", "Gecikmələr (lag) və video keyвələrinin düşməси mümkündür.")
                InfoItem("🟠 -70 … -80 dBm — Pis", "Aşağı sürət, tez-tez qırılmalar, oyunlar üçün uyğun deyil.")
                InfoItem("🔴 -80 … -90 dBm — Çox pis", "İnternet demək olar ki, işləmir.")
                InfoItem("⚫ -90 dBm və daha az", "Siqnal yoxdur.")
            }
        }

        if (showFrequencyPopup) {
            AppDialog(onDismiss = { showFrequencyPopup = false }) {
                Text("Tezlik diapazonları", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text("📶 Wi-Fi 2.4 GHz", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                Text("Uzaq məsafəyə çatır, lakin sürət məhduddur.", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("🚀 Wi-Fi 5 GHz", fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                Text("Çox yüksək sürət, lakin divarlardan zəif keçir.", fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun SpeedtestDialog(onDismiss: () -> Unit) {
    var pingVal by remember { mutableStateOf("-") }
    var downVal by remember { mutableStateOf("-") }
    var statusText by remember { mutableStateOf("Hazır") }
    var isRunning by remember { mutableStateOf(false) }
    val speedHistory = remember { mutableStateListOf<Pair<Float, Float>>() }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }

    fun stopTest() { job?.cancel(); isRunning = false; statusText = "Dayandırıldı" }
    BackHandler { if (isRunning) stopTest() else onDismiss() }

    Dialog(onDismissRequest = { if (!isRunning) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            Column(modifier = Modifier.padding(20.dp).systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sürət Analizi", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(statusText, color = Color.Gray)
                Spacer(modifier = Modifier.height(40.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SpeedResultItem("Ping", pingVal, "ms")
                    SpeedResultItem("Download", downVal, "Mbps")
                }
                Spacer(modifier = Modifier.height(30.dp))
                Box(modifier = Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 10.dp)) {
                    SpeedGraph(speedHistory, Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        if (isRunning) return@Button
                        isRunning = true; speedHistory.clear(); pingVal = "-"; downVal = "-"
                        job = scope.launch(Dispatchers.IO) {
                            statusText = "Ping ölçülür..."
                            val p = measureHighPrecisionPing("149.255.155.105", 8080)
                            pingVal = if (p > 0) "%.2f".format(p) else "Xəta"

                            statusText = "Yükləmə testi (15 san)..."
                            try {
                                val url = "https://sp1.katv1.net.prod.hosts.ooklaserver.net:8080/download?nocache=8781b173&size=690000000"
                                measureDownload(url) { speed, elapsed ->
                                    downVal = "%.1f".format(speed)
                                    speedHistory.add(elapsed to speed)
                                }
                                if (isActive) statusText = "Tamamlandı"
                            } catch (e: Exception) {
                                if (isActive) statusText = "Xəta baş verdi"
                            } finally { isRunning = false }
                        }
                    },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isRunning) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    else Text("BAŞLAT", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { if (isRunning) stopTest() else onDismiss() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(if (isRunning) "LƏĞV ET" else "GERİ", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

suspend fun measureHighPrecisionPing(host: String, port: Int): Double = withContext(Dispatchers.IO) {
    var total = 0.0
    var count = 0
    repeat(3) {
        try {
            val start = System.nanoTime()
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 2000)
            val end = System.nanoTime()
            socket.close()
            total += (end - start) / 1_000_000.0
            count++
        } catch (e: Exception) { }
    }
    if (count > 0) total / count else -1.0
}

suspend fun measureDownload(urlStr: String, onProgress: (Float, Float) -> Unit) {
    withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var totalBytes = 0L
        var lastUpdate = 0L
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        try {
            conn.inputStream.use { input ->
                val buffer = ByteArray(32768)
                while (isActive) {
                    yield()
                    val read = input.read(buffer)
                    if (read == -1) break
                    totalBytes += read
                    val now = System.currentTimeMillis()
                    val elapsedSec = (now - start) / 1000f
                    if (elapsedSec > 15.0f) break
                    if (now - lastUpdate > 300) {
                        if (elapsedSec > 0) {
                            val speed = ((totalBytes * 8) / 1_000_000.0 / elapsedSec).toFloat()
                            onProgress(speed, elapsedSec)
                        }
                        lastUpdate = now
                    }
                }
            }
        } finally { conn.disconnect() }
    }
}

@Composable
fun SpeedGraph(data: List<Pair<Float, Float>>, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val paddingLeft = 100f
        val paddingBottom = 60f
        val graphW = size.width - paddingLeft
        val graphH = size.height - paddingBottom
        val maxSpeed = 100f
        val maxTime = 15f

        val ySteps = listOf(0, 25, 50, 75, 100)
        ySteps.forEach { step ->
            val y = graphH - (step / maxSpeed * graphH)
            drawLine(Color.LightGray.copy(0.4f), Offset(paddingLeft, y), Offset(size.width, y), 1f)
            drawContext.canvas.nativeCanvas.drawText(
                "$step", 10f, y + 10f,
                android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 26f }
            )
        }

        drawLine(Color.Black, Offset(paddingLeft, 0f), Offset(paddingLeft, graphH), 2f)
        drawLine(Color.Black, Offset(paddingLeft, graphH), Offset(size.width, graphH), 2f)

        if (data.isNotEmpty()) {
            val path = Path()
            data.forEachIndexed { i, pair ->
                val time = pair.first
                val speed = pair.second
                val x = paddingLeft + (time / maxTime * graphW).coerceAtMost(graphW)
                val y = graphH - (speed.coerceAtMost(maxSpeed) / maxSpeed * graphH)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFF6200EE), style = Stroke(width = 5f, pathEffect = PathEffect.cornerPathEffect(30f)))
        }

        val xLabels = listOf(0, 3, 6, 9, 12, 15)
        xLabels.forEach { sec ->
            val x = paddingLeft + (sec.toFloat() / maxTime * graphW)
            drawContext.canvas.nativeCanvas.drawText(
                "${sec}s", x, size.height - 10f,
                android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 24f; textAlign = android.graphics.Paint.Align.CENTER }
            )
        }
    }
}

@Composable
fun SpeedResultItem(l: String, v: String, u: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(l, fontSize = 14.sp, color = Color.Gray)
        Text(v, fontSize = 32.sp, fontWeight = FontWeight.Black)
        Text(u, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
    }
}

@Composable
fun RecommendationBlock(currentFreq: Int, results: List<ScanResult>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE3F2FD), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            if (currentFreq > 4900 && currentFreq <= 5925) {
                val bestLow = suggestBest5GHzLow(results)
                Text("Wi-Fi 5 GHz Analizi", fontSize = 14.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Məsləhət görülən kanal: $bestLow", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0D47A1))
                Text("Bu kanallar bütün cihazlar tərəfindən dəstəklənir.", fontSize = 13.sp, color = Color.DarkGray)

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 Əgər routeriniz dəstəkləyirsə, 100-cü kanaldan yuxarı (DFS) kanalları seçmək daha təmiz bağlantı verə bilər.",
                    fontSize = 12.sp, color = Color(0xFF1976D2), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            } else if (currentFreq <= 2484) {
                val best = suggestBestChannel(results)
                Text("Wi-Fi 2.4 GHz Analizi", fontSize = 14.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Məsləhət görülən kanal: $best", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0D47A1))
            } else {
                Text("Wi-Fi Analizi", fontSize = 14.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                Text("6 GHz diapazonu təmizdir.", fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "⚠️ Qeyd: Bəzi cihazlar müəyyən kanalları dəstəkləməyə bilər, bu səbəbdən məsləhətlər tövsiyə xarakteri daşıyır.",
                fontSize = 11.sp, lineHeight = 14.sp, color = Color.Gray
            )
        }
    }
}

@Composable
fun AppDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
            color = Color.Black.copy(alpha = 0.43f)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
                Column(
                    modifier = Modifier.background(Color.White, RoundedCornerShape(20.dp)).padding(20.dp).fillMaxWidth().clickable(enabled = false) { },
                    content = content
                )
            }
        }
    }
}

@Composable
fun InfoItem(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
        Text(desc, fontSize = 13.sp, color = Color.DarkGray)
    }
}

fun suggestBest5GHzLow(results: List<ScanResult>): Int {
    val low5G = listOf(36, 40, 44, 48)
    val scores = mutableMapOf(36 to 0.0, 40 to 0.0, 44 to 0.0, 48 to 0.0)
    results.filter { it.frequency in 5170..5250 }.forEach { result ->
        val chan = convertFrequencyToChannel(result.frequency)
        if (scores.containsKey(chan)) {
            scores[chan] = scores[chan]!! + 10.0.pow(result.level.toDouble() / 10.0)
        }
    }
    return scores.minByOrNull { it.value }?.key ?: 36
}

fun suggestBestChannel(results: List<ScanResult>): Int {
    val standardChannels = listOf(1, 6, 11)
    val scores = mutableMapOf(1 to 0.0, 6 to 0.0, 11 to 0.0)
    results.filter { it.frequency < 4000 }.forEach { result ->
        val channel = convertFrequencyToChannel(result.frequency)
        val weight = 10.0.pow(result.level.toDouble() / 10.0)
        standardChannels.forEach { standard ->
            val distance = kotlin.math.abs(standard - channel)
            if (distance <= 2) scores[standard] = scores[standard]!! + weight / (distance + 1)
        }
    }
    return scores.minByOrNull { it.value }?.key ?: 1
}

suspend fun checkPort(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
    try {
        val socket = Socket()
        socket.connect(InetSocketAddress(ip, port), 4000)
        socket.close()
        true
    } catch (e: Exception) { false }
}

fun calculateSignalColor(dbm: Int): Color {
    val fraction = ((dbm.toFloat() - (-90f)) / (-30f - (-90f))).coerceIn(0f, 1f)
    return lerp(Color(0xDE, 0x06, 0x1A), Color(0x33, 0x99, 0x00), fraction)
}

fun calculateDistance(dbm: Int, frequency: Int): Double = 10.0.pow((-30.0 - dbm) / (10 * if (frequency > 4000) 3.8 else 3.0))

fun convertFrequencyToChannel(freq: Int): Int = when {
    freq == 2484 -> 14
    freq in 2412..2472 -> (freq - 2412) / 5 + 1
    freq in 5170..5825 -> (freq - 5170) / 5 + 34
    freq in 5945..7125 -> (freq - 5945) / 5 + 1
    else -> 0
}

fun getGatewayIp(context: Context): String {
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val gateway = wm.dhcpInfo.gateway
    return if (gateway == 0) "192.168.1.1" else Formatter.formatIpAddress(gateway)
}