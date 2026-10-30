package com.luminex.ai.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminex.ai.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAvailableDialog(
    newVersion: String,
    changelog: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onSkipVersion: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 20.dp),
                text = stringResource(R.string.new_update_available),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                modifier = Modifier.align(Alignment.Start).padding(start = 20.dp),
                text = stringResource(R.string.update_dialog_message, newVersion)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = stringResource(R.string.whats_new),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = changelog,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row (modifier = Modifier.navigationBarsPadding().padding(vertical = 20.dp).align(Alignment.CenterHorizontally)){
                TextButton(
                    onClick = onSkipVersion,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Text(stringResource(R.string.skip_this_version))
                }

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.remind_me_later))
                }

                TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.download))
                }
            }
        }
    }
}
