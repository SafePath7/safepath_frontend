package com.example.safepath_test1.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safepath_test1.ui.components.PageHeader
import com.example.safepath_test1.ui.theme.AppBorder
import com.example.safepath_test1.ui.theme.SafeBlue
import com.example.safepath_test1.ui.theme.TextMuted

@Composable
fun SettingsScreen(
    hasLocationPermission: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val preferences =
        remember(context) {
            context.getSharedPreferences("safe_path_settings", Context.MODE_PRIVATE)
        }
    var sosEnabled by remember { mutableStateOf(preferences.getBoolean("sos_enabled", true)) }
    var showDataUsage by remember { mutableStateOf(false) }
    val versionName =
        remember(context) {
            runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }
                .getOrNull() ?: "-"
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (onBack != null) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .statusBarsPadding()
                        .clickable { onBack() }
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = SafeBlue,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("내 정보로 돌아가기", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SafeBlue)
            }
        }
        PageHeader("설정", "안전 기능과 위치 권한을 관리하세요")
        SettingsGroup(
            rows =
                listOf(
                    Triple("SOS 위치 공유 버튼", sosEnabled) {
                        sosEnabled = it
                        preferences.edit().putBoolean("sos_enabled", it).apply()
                    }
                )
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column {
                SettingsLinkRow(
                    title = "위치 권한",
                    value = if (hasLocationPermission) "허용됨" else "허용 필요",
                ) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = AppBorder)
                SettingsLinkRow("마이크 권한", "위험 음성·음향 감지에 사용") {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = AppBorder)
                SettingsLinkRow("알림 권한", "귀가 상태·위험 확인 알림에 사용") {
                    context.startActivity(
                        if (android.os.Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = AppBorder)
                SettingsLinkRow("데이터 이용 안내", "보기") { showDataUsage = true }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = AppBorder)
                SettingsLinkRow("앱 버전", versionName)
            }
        }
    }

    if (showDataUsage) {
        AlertDialog(
            onDismissRequest = { showDataUsage = false },
            title = { Text("데이터 이용 안내") },
            text = {
                Text(
                    "기존 보호자와 설정 정보는 이 기기에 저장됩니다. 친구 요청을 보내면 이름·연락처·요청 상태가 Firebase 서버에서 처리되고 카카오톡에서 직접 선택한 대상에게 요청 링크를 공유합니다. 링크 수락 시 Google 계정으로 로그인하며 연결된 친구 목록은 서버에 저장됩니다. 장소 검색어와 중심 좌표는 Kakao에, 출발지·도착지 좌표는 Mapbox에 전송됩니다. Google 로그인 시 계정 인증 정보는 Google과 Firebase에서 처리됩니다. 위치 공유는 사용자가 공유 화면에서 앱과 대상을 선택한 경우에만 실행됩니다."
                )
            },
            confirmButton = { TextButton(onClick = { showDataUsage = false }) { Text("확인") } },
        )
    }
}

@Composable
private fun SettingsGroup(rows: List<Triple<String, Boolean, (Boolean) -> Unit>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.first,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Switch(checked = row.second, onCheckedChange = row.third)
                }
                if (index < rows.lastIndex) {
                    HorizontalDivider(Modifier.padding(start = 18.dp), color = AppBorder)
                }
            }
        }
    }
}

@Composable
private fun SettingsLinkRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(value, color = TextMuted, fontSize = 13.sp)
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
