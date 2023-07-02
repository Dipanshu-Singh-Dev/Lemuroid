package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.systems.MetaSystemInfo
import com.swordfish.lemuroid.app.utils.games.GameUtils
import com.swordfish.lemuroid.lib.library.db.entity.Game

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SystemCard(system: MetaSystemInfo, onClick: () -> Unit) {
    val context = LocalContext.current

    val title = remember(system.metaSystem.titleResId) {
        system.getName(context)
    }

    val subtitle = remember(system.metaSystem.titleResId) {
        context.getString(
            R.string.system_grid_details,
            system.count.toString()
        )
    }

    ElevatedCard(
        modifier = Modifier.padding(8.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            SystemImage(system)
            GameTexts(title, subtitle)
        }
    }
}
