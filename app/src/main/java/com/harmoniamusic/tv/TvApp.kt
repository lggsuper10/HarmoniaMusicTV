package com.harmoniamusic.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.harmoniamusic.tv.ui.shell.TvShell
import com.harmoniamusic.tv.ui.theme.TvTheme

@Composable
fun TvApp(onReady: () -> Unit = {}) {
    TvTheme {
        TvShell()
    }
    LaunchedEffect(Unit) { onReady() }
}
