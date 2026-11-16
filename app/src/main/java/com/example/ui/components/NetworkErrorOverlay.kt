package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.Amber400
import com.example.ui.theme.Cyan400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Rose400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.viewmodel.ImagiflyUiState
import com.example.viewmodel.WebErrorCategory
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworkErrorOverlay(
    uiState: ImagiflyUiState,
    onRetryNetwork: () -> Unit,
    onLoadFromCacheFallback: () -> Unit,
    onOpenCacheSettings: () -> Unit,
    onOpenSystemNetworkSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val error = uiState.activeError
    AnimatedVisibility(
        visible = error != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Slate950, Slate900, Slate950)
                        )
                    )
                    .testTag("network_error_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Illustration Banner Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(196.dp),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Slate700.copy(alpha = 0.6f)),
                        colors = CardDefaults.cardColors(containerColor = Slate900)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                painter = painterResource(id = R.drawable.img_network_offline_1794816050038),
                                contentDescription = "网络异常状态插画",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Slate950.copy(alpha = 0.85f)
                                            )
                                        )
                                    )
                            )
                            // Category Status Badge inside illustration bottom
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp),
                                shape = RoundedCornerShape(50),
                                color = Slate900.copy(alpha = 0.9f),
                                border = BorderStroke(
                                    1.dp,
                                    if (error.category == WebErrorCategory.SIMULATED_DRILL) Amber400 else Rose400
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (error.category == WebErrorCategory.SIMULATED_DRILL) {
                                            Icons.Outlined.BugReport
                                        } else {
                                            Icons.Default.CloudOff
                                        },
                                        contentDescription = null,
                                        tint = if (error.category == WebErrorCategory.SIMULATED_DRILL) Amber400 else Rose400,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${error.category.label} · CODE ${error.errorCode}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = when (error.category) {
                            WebErrorCategory.OFFLINE_DISCONNECTED -> "网络连接异常，无法直连 Imagifly"
                            WebErrorCategory.CONNECTION_TIMEOUT -> "连接 Imagifly 服务器超时"
                            WebErrorCategory.SSL_SECURITY_ERROR -> "安全连接证书校验未通过"
                            WebErrorCategory.SERVER_HTTP_ERROR -> "网页暂时无法正常打开"
                            WebErrorCategory.SIMULATED_DRILL -> "网络异常状态提示页 (演练模式)"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("error_title_text")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = error.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCBD5E1),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Diagnostic Telemetry Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate800.copy(alpha = 0.75f)),
                        border = BorderStroke(1.dp, Slate700.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "实时网络与缓存诊断",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Cyan400,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "记录于 ${error.timestamp}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DiagnosticPill(
                                    icon = if (uiState.networkStatus.isOnline) Icons.Outlined.Wifi else Icons.Outlined.SignalWifiOff,
                                    label = if (uiState.networkStatus.isOnline) {
                                        "网络：${uiState.networkStatus.transportType.label}"
                                    } else {
                                        "网络：已断开"
                                    },
                                    accentColor = if (uiState.networkStatus.isOnline) Emerald400 else Rose400
                                )
                                DiagnosticPill(
                                    icon = Icons.Default.Storage,
                                    label = "本地缓存：${formatBytes(uiState.cacheSizeBytes)}",
                                    accentColor = Cyan400
                                )
                                DiagnosticPill(
                                    icon = Icons.Default.History,
                                    label = "离线快照：${uiState.offlineArchives.size} 份",
                                    accentColor = Indigo400
                                )
                            }

                            Text(
                                text = "目标地址：${error.failingUrl}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Primary Action: Retry Network
                    Button(
                        onClick = onRetryNetwork,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("retry_network_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cyan400,
                            contentColor = Slate950
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isSimulatingError) "退出异常演练并刷新网页" else "重新加载网页 (重试网络请求)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Secondary Action: Load from Local Cache / Offline Archive
                    FilledTonalButton(
                        onClick = onLoadFromCacheFallback,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("load_cache_fallback_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Slate800,
                            contentColor = Cyan400
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.offlineArchives.isNotEmpty()) {
                                "使用本地缓存 / 离线快照打开 (${uiState.offlineArchives.size}份可用)"
                            } else {
                                "强制读取本地 WebView 缓存副本"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tertiary Row: System Network Settings & Cache Config Sheet
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenSystemNetworkSettings,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("open_system_network_settings_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Slate700),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFE2E8F0)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsEthernet,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "系统网络设置",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenCacheSettings,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("open_cache_settings_from_error_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Slate700),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFE2E8F0)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "缓存策略管理",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Slate900,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFE2E8F0)
            )
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        return String.format(Locale.US, "%.1f KB", kb)
    }
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}
