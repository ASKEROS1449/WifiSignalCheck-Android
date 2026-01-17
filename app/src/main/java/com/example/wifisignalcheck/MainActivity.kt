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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
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

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasPermission = isGranted }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    if (hasPermission) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        SignalColorApp(wifiManager)
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Lütfən, yerləşmə icazəsini verin", color = Color.White, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun SignalColorApp(wifiManager: WifiManager) {
    val context = LocalContext.current
    var ssid by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(0) }
    var linkSpeed by remember { mutableStateOf(0) }
    var isConnected by remember { mutableStateOf(false) }
    val animatedDbm = remember { Animatable(-100f) }

    var showDbmPopup by remember { mutableStateOf(false) }
    var showFrequencyPopup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val info = wifiManager.connectionInfo
            val currentRssi = info.rssi

            if (currentRssi <= -127 || currentRssi == 0 || info.networkId == -1) {
                isConnected = false
            } else {
                isConnected = true
                val currentSsid = info.ssid.replace("\"", "")
                ssid = if (currentSsid == "<unknown ssid>" || currentSsid.isEmpty()) "WiFi" else currentSsid
                frequency = info.frequency
                linkSpeed = info.linkSpeed

                animatedDbm.animateTo(
                    targetValue = currentRssi.toFloat(),
                    animationSpec = tween(durationMillis = 300, easing = LinearEasing)
                )
            }
            delay(500)
        }
    }

    val minSignalColor = Color(0xDE, 0x06, 0x1A)
    val targetColor = if (isConnected) calculateSignalColor(animatedDbm.value.toInt()) else minSignalColor

    val animatedBgColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 500),
        label = "BgColor"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(animatedBgColor),
        contentAlignment = Alignment.Center
    ) {
        if (!isConnected) {
            Text(
                text = "Cihazınız Wi-Fi şəbəkəsinə\nbağlı deyil",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showFrequencyPopup = true }
                ) {
                    Text(
                        text = "SSID: $ssid",
                        fontSize = 22.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val ghz = if (frequency > 4000) "5 GHz" else "2.4 GHz"
                    val channel = convertFrequencyToChannel(frequency)
                    Text(
                        text = "$ghz — Kanal: $channel — $linkSpeed Mbps",
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                Text(
                    text = "${animatedDbm.value.toInt()} dBm",
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showDbmPopup = true }
                )

                Spacer(modifier = Modifier.height(10.dp))

                val distance = calculateDistance(animatedDbm.value.toInt(), frequency)
                Text(
                    text = "Təxmini məsafə: ${"%.1f".format(distance)} m",
                    fontSize = 20.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "(divarlar və maneələr nəzərə alınmadan)",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        val gatewayIp = getGatewayIp(context)
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$gatewayIp"))
                        context.startActivity(browserIntent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.25f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(50.dp)
                ) {
                    Text(text = "Router ayarlarına daxil ol", fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        if (showDbmPopup) {
            AppDialog(onDismiss = { showDbmPopup = false }) {
                Text(text = "Siqnal Səviyyəsi Cədvəli", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                InfoItem("🟢 -30 … -50 dBm — Əla", "Router yaxındadır, maksimal sürət, 4K video və oyunlar üçün idealdır.")
                InfoItem("🟢 -50 … -60 dBm — Çox yaxşı", "Stabil internet, iş və yayım (stream) üçün uyğundur.")
                InfoItem("🟡 -60 … -67 dBm — Normal", "İnternet stabil işləyir, lakin sürət maksimumdan aşağı ola bilər.")
                InfoItem("🟡 -67 … -70 dBm — Limit", "Gecikmələr (lag) və video keyfiyyətinin düşməsi mümkündür.")
                InfoItem("🟠 -70 … -80 dBm — Pis", "Aşağı sürət, tez-tez qırılmalar, oyunlar üçün uyğun deyil.")
                InfoItem("🔴 -80 … -90 dBm — Çox pis", "İnternet demək olar ki, işləmir.")
                InfoItem("⚫ -90 dBm və daha az", "Siqnal yoxdur.")
            }
        }

        if (showFrequencyPopup) {
            AppDialog(onDismiss = { showFrequencyPopup = false }) {
                Text(text = "Tezlik diapazonları", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text("📶 Wi-Fi 2.4 GHz", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF2E7D32))
                Text("Daha yavaş, lakin daha uzaq", fontSize = 14.sp, color = Color.Gray)
                Text("✔ Müsbət: Daha uzaq məsafə, divarlardan daha yaxşı keçir.", fontSize = 13.sp, color = Color.DarkGray)
                Text("❌ Mənfi: Aşağı sürət, qonşu routerlər tərəfindən tez-tez yüklənir.", fontSize = 13.sp, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(12.dp))
                Text("🚀 Wi-Fi 5 GHz", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF1565C0))
                Text("Daha sürətli, lakin daha yaxın", fontSize = 14.sp, color = Color.Gray)
                Text("✔ Müsbət: Yüksək sürət, az maneə. Oyunlar və 4K üçün əladır.", fontSize = 13.sp, color = Color.DarkGray)
                Text("❌ Mənfi: Kiçik radius, divarlardan zəif keçir.", fontSize = 13.sp, color = Color.DarkGray)
            }
        }
    }
}

@Composable
fun AppDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
            color = Color.Black.copy(alpha = 0.43f) // Изменено с 0.73f на 0.43f
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
                Column(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .clickable(enabled = false) { },
                    content = content
                )
            }
        }
    }
}

@Composable
fun InfoItem(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
        Text(text = desc, fontSize = 13.sp, color = Color.DarkGray)
    }
}

fun calculateSignalColor(dbm: Int): Color {
    if (dbm <= -100 || dbm >= 0) return Color(0xDE, 0x06, 0x1A)
    val fraction = ((dbm.toFloat() - (-90f)) / (-30f - (-90f))).coerceIn(0f, 1f)
    return lerp(Color(0xDE, 0x06, 0x1A), Color(0x33, 0x99, 0x00), fraction)
}

fun calculateDistance(dbm: Int, frequency: Int): Double {
    if (dbm >= -25) return 0.5
    val environmentalFactor = if (frequency > 4000) 3.8 else 3.0
    val measuredPower = -30.0
    val exp = (measuredPower - dbm) / (10 * environmentalFactor)
    return 10.0.pow(exp)
}

fun convertFrequencyToChannel(freq: Int): Int {
    return when {
        freq == 2484 -> 14
        freq in 2412..2472 -> (freq - 2412) / 5 + 1
        freq in 5170..5825 -> (freq - 5170) / 5 + 34
        else -> 0
    }
}

@Suppress("DEPRECATION")
fun getGatewayIp(context: Context): String {
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val gateway = wm.dhcpInfo.gateway
    return if (gateway == 0) "192.168.1.1" else Formatter.formatIpAddress(gateway)
}