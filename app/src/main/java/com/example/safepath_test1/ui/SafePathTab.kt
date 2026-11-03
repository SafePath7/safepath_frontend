package com.example.safepath_test1.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector

enum class SafePathTab(val label: String, val icon: ImageVector) {
    Home("홈", Icons.Filled.Home),
    Friends("친구", Icons.Filled.People),
    Guardian("보호", Icons.Filled.Shield),
    Profile("내 정보", Icons.Filled.Person),
}
