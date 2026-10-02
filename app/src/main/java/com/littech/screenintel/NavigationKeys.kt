package com.littech.screenintel

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Main : NavKey

@Serializable
data class Viewer(val screenshotId: Long) : NavKey

@Serializable
data object Cleanup : NavKey

