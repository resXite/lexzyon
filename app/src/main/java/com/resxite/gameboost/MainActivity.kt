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
    var shz by remember { mutableIntStateOf(0) }
    var adbOk by remember { mutableStateOf(false) }
    var skipped by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var games by remember { mutableStateOf(Store.games()) }
    val ready = shz == 3 || adbOk

    LaunchedEffect(Unit) {
        while (true) {
            val r = withContext(Dispatchers.IO) { Pair(Shz.state(ctx), Adb.adbConnected(ctx)) }
            shz = r.first
            adbOk = r.second
            if (shz == 3 || adbOk) Store.onboarded = true
            delay(2000)
        }
    }

    Box(Modifier.fillMaxSize().background(Bg).navigationBarsPadding()) {
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
            !ready && !Store.onboarded && !skipped -> Page {
                ConnectionContent(ac, shz, adbOk, "Aktifkan koneksi") { skipped = true }
            }
            else -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> GameTab(minPx, ac, ready, shz, adbOk, games, { games = Store.games() }, { picker = true }, { tab = 1 })
                        1 -> Page { ConnectionContent(ac, shz, adbOk, "Koneksi", null) }
                        else -> SettingsTab(ac, idx) { idx = it; Store.theme = it }
                    }
                }
                NavBar(tab, ac) { tab = it }
            }
        }
    }
}

@Composable
private fun NavBar(tab: Int, ac: Color, onTab: (Int) -> Unit) {
    val items = listOf("Game", "Koneksi", "Pengaturan")
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEachIndexed { i, t ->
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (i == tab) ac.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onTab(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t,
                    color = if (i == tab) ac else Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
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
            disabledContainerColor = Bg, disabledContentColor = Muted
        )
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Bg).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
private fun ColumnScope.ConnectionContent(
    ac: Color, shz: Int, adbOk: Boolean, title: String, onSkip: (() -> Unit)?
) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (onSkip != null) "SEKALI SAJA" else "PILIH SALAH SATU", color = Muted, fontSize = 13.sp)
        Text(title, color = Txt, fontSize = 32.sp, fontWeight = FontWeight.Medium)
    }

    PanelBox {
        Text("Shizuku", color = Txt, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(
            when (shz) {
                0 -> "Belum terpasang"
                1 -> "Terpasang, belum dijalankan"
                2 -> "Berjalan, belum diberi izin"
                else -> "Siap dipakai"
            },
            color = if (shz == 3) ac else Muted, fontSize = 14.sp
        )
        Text(
            "Cara tercepat kalau Shizuku sudah terpasang dan dijalankan. Shizuku diprioritaskan dibanding ADB.",
            color = Muted, fontSize = 13.sp
        )
        when (shz) {
            0 -> Big("Unduh Shizuku") {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
            }
            1 -> Big("Buka Shizuku") {
                ctx.packageManager.getLaunchIntentForPackage(Shz.PKG)?.let { ctx.startActivity(it) }
            }
            2 -> Big("Beri izin Shizuku") { Shz.request() }
        }
    }

    PanelBox {
        Text("ADB nirkabel", color = Txt, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(
            if (adbOk) "Terhubung" else "Belum terhubung",
            color = if (adbOk) ac else Muted, fontSize = 14.sp
        )
        Text("1. Ketuk Mulai pairing, notifikasi akan muncul.", color = Muted, fontSize = 13.sp)
        Text(
            "2. Di Opsi pengembang, nyalakan Wireless debugging lalu pilih Pasangkan dengan kode.",
            color = Muted, fontSize = 13.sp
        )
        Text("3. Tarik notifikasi, ketik kode 6 digit, lalu kirim.", color = Muted, fontSize = 13.sp)
        Big("Mulai pairing") {
            ctx.startForegroundService(Intent(ctx, BoostService::class.java).setAction("pair"))
            Toast.makeText(ctx, "Notifikasi pairing muncul", Toast.LENGTH_SHORT).show()
        }
        Big("Buka Opsi Pengembang") {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
        TextButton(
            onClick = { Thread { Adb.connect(ctx) }.start() },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("Sambungkan ulang", color = Muted, fontSize = 14.sp) }
    }

    if (onSkip != null) {
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Lewati dulu", color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun GameTab(
    minPx: Int, ac: Color, ready: Boolean, shz: Int, adbOk: Boolean,
    games: List<Pair<String, Int>>,
    onChanged: () -> Unit, onAdd: () -> Unit, onConnect: () -> Unit
) {
    val ctx = LocalContext.current
    val cfg = ctx.resources.configuration
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
                .background(if (ready) ac.copy(alpha = 0.14f) else Panel)
                .clickable(enabled = !ready) { onConnect() }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                when {
                    shz == 3 -> "Shizuku aktif"
                    adbOk -> "ADB terhubung"
                    else -> "Belum terhubung · ketuk untuk menyambung"
                },
                color = if (ready) ac else Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
        }

        if (games.isEmpty()) {
            PanelBox {
                Text("Belum ada aplikasi. Ketuk Tambah aplikasi untuk memilih game.", color = Muted, fontSize = 14.sp)
            }
        }
        games.forEach { (pkg, dpi) ->
            GameCard(pkg, dpi, minPx, ac, ready) {
                Store.removeGame(pkg)
                onChanged()
            }
        }
        Big("Tambah aplikasi", onClick = onAdd)
    }
}

@Composable
private fun SettingsTab(ac: Color, idx: Int, onTheme: (Int) -> Unit) {
    val ctx = LocalContext.current
    var floating by remember { mutableStateOf(Store.floating) }
    Page {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ATUR SESUKAMU", color = Muted, fontSize = 13.sp)
            Text("Pengaturan", color = Txt, fontSize = 32.sp, fontWeight = FontWeight.Medium)
        }

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

        PanelBox {
            Text("Darurat", color = Txt, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("Kembalikan density ke nilai asli HP kalau tampilan masih terlalu besar.", color = Muted, fontSize = 13.sp)
            Big("Pulihkan density ke normal") {
                Thread {
                    if (!Adb.connected(ctx)) Adb.connect(ctx)
                    Adb.run(ctx, "wm density reset")
                }.start()
                Toast.makeText(ctx, "Density dikembalikan", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun GameCard(
    pkg: String, dpi: Int, minPx: Int, ac: Color, ready: Boolean, onRemove: () -> Unit
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
        Big(if (ready) "Main" else "Sambungkan dulu di tab Koneksi", enabled = ready) {
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
        Modifier.fillMaxSize().background(Bg).statusBarsPadding().padding(18.dp),
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
