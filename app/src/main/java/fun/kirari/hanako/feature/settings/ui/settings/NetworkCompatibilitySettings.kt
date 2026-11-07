package `fun`.kirari.hanako.feature.settings.ui.settings

import androidx.compose.runtime.Composable

@Composable
internal fun TrustAllHttpsSwitch(
    trustAllHttpsCertificates: Boolean,
    onToggleTrustAllHttpsCertificates: (Boolean) -> Unit
) {
    SwitchSettingRow(
        title = "信任所有 HTTPS 证书",
        subtitle = "允许连接自签名、过期或域名不匹配的 HTTPS 服务。存在安全风险，仅建议用于个人测试接口。HTTP 明文访问已允许。",
        checked = trustAllHttpsCertificates,
        onCheckedChange = onToggleTrustAllHttpsCertificates
    )
}
