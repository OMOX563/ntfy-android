package io.heckel.ntfy.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * 编译链路探针（临时）
 *
 * 目的只有一个：在真正重写 UI 之前，先确认这套 AGP 9.0.0 + Gradle 9.2.1 + Kotlin 2.2.10
 * 能把 Compose 编译过。它和业务无关，验证通过后整个文件删除。
 */
@Composable
fun ComposeProbe() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("compose ok")
    }
}

@Preview(showBackground = true)
@Composable
private fun ComposeProbePreview() {
    ComposeProbe()
}
