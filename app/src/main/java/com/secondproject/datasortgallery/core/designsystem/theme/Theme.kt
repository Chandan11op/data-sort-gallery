package com.secondproject.datasortgallery.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GalleryPrimaryDark,
    onPrimary = GalleryOnPrimaryDark,
    primaryContainer = GalleryPrimaryContainerDark,
    onPrimaryContainer = GalleryOnPrimaryContainerDark,
    secondary = GallerySecondaryDark,
    onSecondary = GalleryOnSecondaryDark,
    secondaryContainer = GallerySecondaryContainerDark,
    onSecondaryContainer = GalleryOnSecondaryContainerDark,
    tertiary = GalleryTertiaryDark,
    onTertiary = GalleryOnTertiaryDark,
    tertiaryContainer = GalleryTertiaryContainerDark,
    onTertiaryContainer = GalleryOnTertiaryContainerDark,
    background = GalleryBackgroundDark,
    onBackground = GalleryOnBackgroundDark,
    surface = GallerySurfaceDark,
    onSurface = GalleryOnSurfaceDark,
    surfaceVariant = GallerySurfaceVariantDark,
    onSurfaceVariant = GalleryOnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = GalleryPrimaryLight,
    onPrimary = GalleryOnPrimaryLight,
    primaryContainer = GalleryPrimaryContainerLight,
    onPrimaryContainer = GalleryOnPrimaryContainerLight,
    secondary = GallerySecondaryLight,
    onSecondary = GalleryOnSecondaryLight,
    secondaryContainer = GallerySecondaryContainerLight,
    onSecondaryContainer = GalleryOnSecondaryContainerLight,
    background = GalleryBackgroundLight,
    onBackground = GalleryOnBackgroundLight,
    surface = GallerySurfaceLight,
    onSurface = GalleryOnSurfaceLight,
    surfaceVariant = GallerySurfaceVariantLight,
    onSurfaceVariant = GalleryOnSurfaceVariantLight
)

@Composable
fun DataSortGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}