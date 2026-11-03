package com.example.safepath_test1.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.safepath_test1.ui.SafePathTab
import com.example.safepath_test1.ui.theme.SafeBlue
import com.example.safepath_test1.ui.theme.TextMuted

val FloatingPanelShape = RoundedCornerShape(22.dp)

@Composable
fun SafePathBottomBar(
    selectedTab: SafePathTab,
    onTabSelected: (SafePathTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
        color = Color.White,
        shadowElevation = 0.dp,
    ) {
        NavigationBar(
            modifier = Modifier.height(68.dp),
            windowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.White,
            tonalElevation = 0.dp,
        ) {
            SafePathTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = tab == selectedTab,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                    },
                    label = { Text(tab.label) },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = SafeBlue,
                            selectedTextColor = SafeBlue,
                            indicatorColor = SafeBlue.copy(alpha = .10f),
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                        ),
                )
            }
        }
    }
}
