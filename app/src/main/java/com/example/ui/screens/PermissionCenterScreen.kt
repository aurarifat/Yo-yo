package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.PermissionCenterItem
import com.example.engine.PermissionItemId
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.SlateCard
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoltGreen

@Composable
fun PermissionCenterScreen(
    permissions: List<PermissionCenterItem>,
    onOpenSettings: (PermissionItemId) -> Unit,
    onRecheckAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PERMISSION CENTER",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Real-time verification of every system, overlay & ADB permission",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                OutlinedButton(
                    onClick = onRecheckAll,
                    modifier = Modifier.testTag("recheck_all_permissions_button")
                ) {
                    Text("RECHECK ALL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        items(permissions, key = { it.id.name }) { item ->
            PermissionItemCard(
                item = item,
                onOpenSettings = { onOpenSettings(item.id) },
                onRecheck = onRecheckAll
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun PermissionItemCard(
    item: PermissionCenterItem,
    onOpenSettings: () -> Unit,
    onRecheck: () -> Unit
) {
    val badgeColor = if (item.isGranted) VoltGreen else AmberWarn

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, badgeColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .testTag("permission_card_${item.id.name.lowercase()}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = badgeColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = item.statusText,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "WHY REQUIRED: ${item.whyRequired}",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("permission_open_${item.id.name.lowercase()}")
                ) {
                    Text(
                        text = item.actionLabel,
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = onRecheck,
                    modifier = Modifier
                        .weight(0.7f)
                        .testTag("permission_recheck_${item.id.name.lowercase()}")
                ) {
                    Text("RECHECK", fontSize = 11.sp)
                }
            }
        }
    }
}
