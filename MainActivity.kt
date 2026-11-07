package com.resxite.gameboost

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF08090A)
private val Panel = Color(0xFF14171B)
private val Muted = Color(0xFF8A9099)
private val Txt = Color(0xFFF2F4F7)

class MainActivity : ComponentActivity() {
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        val b = windowManager.maximumWindowMetrics.bounds
        val minPx = minOf(b.width(), b.height())
        Thread { if (Store.onboarded) Adb.connect(this) }.start()
        setContent { Root(minPx) }
    }
}

@Composable
fun Root(minPx: Int) {
    val ctx = LocalContext.current
    var idx by remember { mutableIntStateOf(Store.theme) }
    val ac = Color(Store.ACCENTS[idx])
    var connected by remember { mutableStateOf(false) }
    var skipped by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var games by remember { mutableStateOf(Store.games()) }

    LaunchedEffect(Unit) {
        while (true) {
            connected = withContext(Dispatchers.IO) { Adb.connected(ctx) }
            if (connected) Store.onboarded = true
            delay(2000)
        }
    }

    when {
        picker -> Picker(
            ac,
            { pkg ->
                Store.setGame(pkg, 720)
                games = Store.games()
                picker = false
            },
            { picker = false }
        )
        !connected && !Store.onboarded && !skipped -> Onboarding(ac) { skipped = true }
        else -> Home(
            minPx, ac, connected, idx,
            { idx = it; Store.theme = it },
            games,
            { games = Store.games() },
            { picker = true }
        )
    }
}

@Composable
private fun PanelBox(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun Big(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Txt, contentColor = Bg,
            disabledContainerColor = Panel, disabledContentColor = Muted
        )
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
fun Onboarding(ac: Color, onSkip: () -> Unit) {
    val ctx = LocalContext.current
    Page {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SEKALI SAJA", color = Muted, fontSize = 13.sp)
            Text("Aktifkan ADB", color = Txt, fontSize = 32.sp, fontWeight = FontWeight.Medium)
        }
        PanelBox {
            Text("1. Ketuk Mulai pairing di bawah. Notifikasi akan muncul.", color = Txt, fontSize = 15.sp)
            Text("2. Buka Opsi pengembang, nyalakan Wireless debugging, lalu pilih Pasangkan perangkat dengan kode pairing.", color = Txt, fontSize = 15.sp)
            Text("3. Tarik notifikasi ke bawah, ketik kode 6 digit, lalu kirim.", color = Txt, fontSize = 15.sp)
        }
        Big("Mulai pairing") {
            ctx.startForegroundService(Intent(ctx, BoostService::class.java).setAction("pair"))
            Toast.makeText(ctx, "Notifikasi pairing muncul", Toast.LENGTH_SHORT).show()
        }
        Big("Buka Opsi Pengembang") {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Lewati dulu", color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
fun Home(
    minPx: Int, ac: Color, connected: Boolean, idx: Int,
    onTheme: (Int) -> Unit,
    games: List<Pair<String, Int>>,
    onChanged: () -> Unit,
    onAdd: () -> Unit
) {
    val ctx = LocalContext.current
    val cfg = ctx.resources.configuration
    var floating by remember { mutableStateOf(Store.floating) }

    Page {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Density ${cfg.densityDpi} · Lebar terkecil ${cfg.smallestScreenWidthDp} dp",
                color = Muted, fontSize = 13.sp
            )
            Text("Game Booster", color = Txt, fontSize = 32.sp, fontWeight = FontWeight.Medium)
        }

        Box(
            Modifier.clip(RoundedCornerShape(99.dp))
                .background(if (connected) ac.copy(alpha = 0.14f) else Panel)
                .clickable(enabled = !connected) { Thread { Adb.connect(ctx) }.start() }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                if (connected) "ADB terhubung" else "ADB terputus · ketuk untuk menyambung",
                color = if (connected) ac else Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
        }

        if (games.isEmpty()) {
            PanelBox {
                Text("Belum ada aplikasi. Ketuk Tambah aplikasi untuk memilih game.", color = Muted, fontSize = 14.sp)
            }
        }
        games.forEach { (pkg, dpi) ->
            GameCard(pkg, dpi, minPx, ac, connected) {
                Store.removeGame(pkg)
                onChanged()
            }
        }
        Big("Tambah aplikasi", onClick = onAdd)

        PanelBox {
            Text("Warna tema", color = Txt, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Store.ACCENTS.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                            .then(if (i == idx) Modifier.border(3.dp, Txt, CircleShape) else Modifier)
                            .clickable { onTheme(i) }
                    )
                }
            }
            Text("Merah · Biru · Putih · Hijau · Ungu · Pink", color = Muted, fontSize = 12.sp)
        }

        PanelBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tombol melayang", color = Txt, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Tombol Pulihkan tampil di atas game", color = Muted, fontSize = 13.sp)
                }
                Switch(
                    checked = floating,
                    onCheckedChange = { on ->
                        floating = on
                        Store.floating = on
                        if (on && !Settings.canDrawOverlays(ctx)) {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = ac, checkedThumbColor = Bg)
                )
            }
        }

        TextButton(
            onClick = {
                Thread {
                    if (!Adb.connected(ctx)) Adb.connect(ctx)
                    Adb.run(ctx, "wm density reset")
                }.start()
                Toast.makeText(ctx, "Density dikembalikan", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("Pulihkan density ke normal", color = Muted, fontSize = 14.sp) }
    }
}

@Composable
private fun GameCard(
    pkg: String, dpi: Int, minPx: Int, ac: Color, connected: Boolean, onRemove: () -> Unit
) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val label = remember(pkg) {
        try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
    }
    val icon = remember(pkg) {
        try { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() } catch (e: Exception) { null }
    }
    var v by remember(pkg) { mutableIntStateOf(dpi) }
    val wdp = (minPx * 160f / v).roundToInt()

    PanelBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) {
                Image(icon, null, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
            }
            Column(Modifier.weight(1f)) {
                Text(label, color = Txt, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Density $v · lebar $wdp dp", color = ac, fontSize = 13.sp)
            }
            TextButton(onClick = onRemove) { Text("Hapus", color = Muted, fontSize = 13.sp) }
        }
        Slider(
            value = v.toFloat(),
            onValueChange = { v = ((it / 10).roundToInt() * 10).coerceIn(320, 1440) },
            onValueChangeFinished = { Store.setGame(pkg, v) },
            valueRange = 320f..1440f,
            colors = SliderDefaults.colors(
                thumbColor = Txt, activeTrackColor = ac, inactiveTrackColor = Color(0xFF1E2227)
            )
        )
        Big(if (connected) "Main" else "Sambungkan ADB dulu", enabled = connected) {
            Store.setGame(pkg, v)
            ctx.startForegroundService(
                Intent(ctx, BoostService::class.java).setAction("boost")
                    .putExtra("pkg", pkg).putExtra("dpi", v)
            )
        }
    }
}

private fun loadApps(ctx: Context): List<Pair<String, String>> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .distinctBy { it.first }
        .filter { it.first != ctx.packageName }
        .sortedBy { it.second.lowercase() }
}

@Composable
fun Picker(ac: Color, onPick: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onClose)
    val apps = remember { loadApps(ctx) }
    var q by remember { mutableStateOf("") }
    val shown = apps.filter { it.second.contains(q, ignoreCase = true) }

    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pilih aplikasi", color = Txt, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            placeholder = { Text("Cari") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Txt, unfocusedTextColor = Txt,
                focusedBorderColor = ac, unfocusedBorderColor = Panel, cursorColor = ac
            )
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.first }) { (pkg, label) ->
                val icon = remember(pkg) {
                    try { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() } catch (e: Exception) { null }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
                        .clickable { onPick(pkg) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (icon != null) Image(icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                    Text(label, color = Txt, fontSize = 15.sp)
                }
            }
        }
    }
}
