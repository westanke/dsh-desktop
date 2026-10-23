package io.github.westanke.dshdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.westanke.dshdesktop.AppState
import io.github.westanke.dshdesktop.ConnectionState
import io.github.westanke.dshdesktop.importDsh1Config

/**
 * 连接页。
 *
 * 这是用户看到的第一屏，任务只有一个：把「地址 + 一次性配对码」变成一条可用的设备令牌。
 * 因此不放任何导航、不放功能介绍的段落，只保留三步事实与失败原因。
 */
@Composable
fun ConnectScreen(state: AppState) {
    val colors = LocalAppColors.current
    var endpoint by remember {
        mutableStateOf(state.profile.endpoint.removeSuffix("/api/v1").removePrefix("http://").removePrefix("https://"))
    }
    var code by remember { mutableStateOf("") }
    val busy = state.connection == ConnectionState.Connecting

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(560.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("DS", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("DSH Desktop", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(
                "连接这台机器上的 DeepSeek Harness",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(26.dp))

            AppCard(Modifier.fillMaxWidth(), padding = 22) {
                SectionLabel("① 服务器地址")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    placeholder = { Text("192.168.1.20:3090", style = MaterialTheme.typography.bodyMedium) },
                    shape = RoundedCornerShape(Tokens.radiusSm),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "填能到达这台机器的 IP 或域名，端口默认 3090。程序会自动补成 http://…/api/v1。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )

                Spacer(Modifier.height(18.dp))
                SectionLabel("② 一次性配对码")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    placeholder = { Text("在 DSH「手机设置 → 远程访问」里生成", style = MaterialTheme.typography.bodyMedium) },
                    shape = RoundedCornerShape(Tokens.radiusSm),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "配对码 10 分钟内有效且只能用一次。已经配过对的机器可以留空，直接用已保存的令牌连接。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )

                Spacer(Modifier.height(18.dp))
                SectionLabel("③ 或粘贴 DSH1 配置导入")
                Spacer(Modifier.height(6.dp))
                var showImport by remember { mutableStateOf(false) }
                if (!showImport) {
                    TextButton(
                        onClick = { showImport = true },
                        enabled = !busy,
                        shape = RoundedCornerShape(Tokens.radiusSm),
                    ) {
                        Text("粘贴 DSH1 配置导入", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    // 这段文本等同于设备令牌（见 data/ConnectionShare.kt 的安全边界）：
                    // 无论导入成败都立即清空，不让它留在输入框里被截图/录屏带走。
                    var configText by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = configText,
                        onValueChange = { configText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp),
                        enabled = !busy,
                        placeholder = { Text("DSH1:…", style = MaterialTheme.typography.bodyMedium) },
                        shape = RoundedCornerShape(Tokens.radiusSm),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "在电脑上生成这段文本（含地址与设备令牌），经微信/邮件发给自己，再粘贴到这里。" +
                            "它等同于钥匙，别转发他人；导入会替换当前连接配置。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                state.importDsh1Config(configText) { filled ->
                                    // 与表单初始值同一约定：地址栏不显示协议前缀
                                    endpoint = filled.removePrefix("http://").removePrefix("https://")
                                }
                                configText = ""
                            },
                            enabled = !busy && configText.isNotBlank(),
                            shape = RoundedCornerShape(Tokens.radiusSm),
                            modifier = Modifier.height(42.dp),
                        ) {
                            Text("导入并连接")
                        }
                        TextButton(onClick = { showImport = false }, enabled = !busy) {
                            Text("收起", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (state.importNotice != null) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        color = colors.mutedSurface,
                        shape = RoundedCornerShape(Tokens.radiusSm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            state.importNotice.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                if (state.connectionError != null) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(Tokens.radiusSm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            state.connectionError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { state.connect(endpoint, code.ifBlank { null }) },
                        enabled = !busy && endpoint.isNotBlank(),
                        shape = RoundedCornerShape(Tokens.radiusSm),
                        modifier = Modifier.height(42.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (busy) "正在连接…" else "连接")
                    }
                    if (state.token != null) {
                        TextButton(onClick = { state.disconnect(clearToken = true) }, enabled = !busy) {
                            Text("清除已保存的令牌", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "远程访问默认是关闭的。请先在电脑上打开 DSH 设置 →「手机设置」→ 远程访问，" +
                    "保存绑定地址并点「启用并创建配对」。只有回环地址的管理界面能生成配对码。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
