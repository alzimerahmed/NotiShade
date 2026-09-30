package app.sift.ui

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Compose-side plural lookup; [count] is both the plural selector and the first format arg. */
@Composable
fun pluralResource(@PluralsRes plural: Int, count: Int, vararg args: Any): String =
    LocalContext.current.resources.getQuantityString(plural, count, count, *args)
