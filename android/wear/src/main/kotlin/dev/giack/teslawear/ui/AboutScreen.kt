package dev.giack.teslawear.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.giack.teslawear.R
import dev.giack.teslawear.ui.theme.TeslaColors

/** Name, version, licence and the statement that the app is not made by Tesla or Google. */
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    val state = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding) {
            item { ListHeader { Text(stringResource(R.string.about_title)) } }
            item { Paragraph(stringResource(R.string.app_name), size = 16, color = TeslaColors.Ink) }
            item { Paragraph(stringResource(R.string.about_version, version)) }
            item { Paragraph(stringResource(R.string.about_licence)) }
            item { Paragraph(stringResource(R.string.about_disclaimer), color = TeslaColors.Ink) }
            item { Paragraph(stringResource(R.string.about_trademarks), size = 11) }
        }
    }
}

@Composable
private fun Paragraph(value: String, size: Int = 13, color: androidx.compose.ui.graphics.Color = TeslaColors.Muted) {
    Text(
        value, fontSize = size.sp, color = color, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}
