package com.example.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.components.PdfViewerContent

/**
 * Dedicated, full-screen PDF & Lecture Slide Viewer Screen.
 * Fully decoupled from any background video player or dialog window.
 */
@Composable
fun PdfViewerScreen(
    slideUrl: String,
    title: String,
    initialRemoteUrl: String? = null,
    subject: String = "",
    onBack: () -> Unit
) {
    PdfViewerContent(
        slideUrl = slideUrl,
        title = title,
        initialRemoteUrl = initialRemoteUrl,
        onBack = onBack
    )
}
