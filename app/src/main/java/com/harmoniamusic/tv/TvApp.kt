package com.harmoniamusic.tv

import androidx.compose.runtime.Composable
import com.harmoniamusic.tv.ui.shell.TvShell
import com.harmoniamusic.tv.ui.theme.TvTheme

@Composable
fun TvApp() {
    TvTheme {
        TvShell()
    }
}
