package com.example.wifisignalcheck

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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

    var showDbmPopup by rememberSaveable { mutableStateOf(false) }
    var showFrequencyPopup by rememberSaveable { mutableStateOf(false) }
    var showIpPopup by rememberSaveable { mutableStateOf(false) }

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
            Text("Bağlı deyil", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
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

                Button(
                    onClick = {
                        val ip = getGatewayIp(context)
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://$ip")))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(50.dp)
                ) {
                    Text("Router ayarlarına daxil ol", fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
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
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF5F5F5),
                            unfocusedContainerColor = Color(0xFFF5F5F5),
                            focusedIndicatorColor = Color(0xFF2196F3),
                            unfocusedIndicatorColor = Color.Transparent
                        )
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
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
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
                InfoItem("🟢 -30 … -50 dBm — Əla", "Maksimal sürət üçün ideal zona.")
                InfoItem("🟡 -60 … -70 dBm — Orta", "Stabil internet, lakin məsafə artıb.")
                InfoItem("🔴 -80 … -90 dBm — Pis", "Kəsilmələr və aşağı sürət.")
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
fun AppDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
            color = Color.Black.copy(alpha = 0.43f)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
                Column(
                    modifier = Modifier.background(Color.White, RoundedCornerShape(20.dp)).padding(20.dp).fillMaxWidth().verticalScroll(rememberScrollState()).clickable(enabled = false) { },
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

suspend fun checkPort(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
    try {
        val socket = Socket()
        socket.connect(InetSocketAddress(ip, port), 4000)
        socket.close()
        true
    } catch (e: Exception) {
        false
    }
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
    else -> 0
}

fun getGatewayIp(context: Context): String {
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val gateway = wm.dhcpInfo.gateway
    return if (gateway == 0) "192.168.1.1" else Formatter.formatIpAddress(gateway)
}