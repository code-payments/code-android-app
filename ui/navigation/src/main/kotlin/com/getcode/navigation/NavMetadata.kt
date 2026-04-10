package com.getcode.navigation

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.getcode.navigation.results.NavResultKey
import com.getcode.navigation.results.NavigationRetVal
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.allSupertypes

enum class NavMetadataKeys(val key: String, ) {
    IsNonDismissable("non_dismissable"),
    IsNonDraggable("non_draggable"),
    IsSheet("sheet"),
    IsSolitarySheet("sheet_solitary"),
    NavResultKey("navresult_key"),
}

/**
 * DSL helper: registers an entry whose metadata is derived from [T]'s marker interfaces.
 */
inline fun <reified T : NavKey> EntryProviderScope<NavKey>.annotatedEntry(
    noinline content: @Composable (T) -> Unit
) {
    entry(metadata = T::class.metadata(), content = content)
}

/**
 * Compute metadata from a [KClass] by inspecting its marker interfaces.
 */
fun KClass<*>.metadata(): Map<String, Any> {
    val retValType: KType? = allSupertypes
        .find { it.classifier == NavigationRetVal::class }
    val resultClass = retValType?.arguments?.firstOrNull()?.type?.classifier as? KClass<*>

    return mapOf(
        NavMetadataKeys.IsSheet.key to Sheet::class.java.isAssignableFrom(this.java),
        NavMetadataKeys.IsSolitarySheet.key to SolitarySheet::class.java.isAssignableFrom(this.java),
        NavMetadataKeys.IsNonDismissable.key to NonDismissableRoute::class.java.isAssignableFrom(this.java),
        NavMetadataKeys.IsNonDraggable.key to NonDraggableRoute::class.java.isAssignableFrom(this.java),
        NavMetadataKeys.NavResultKey.key to (if (resultClass != null) {
            @Suppress("UNCHECKED_CAST")
            NavResultKey(
                this as KClass<NavigationRetVal<Parcelable>>,
                resultClass as KClass<Parcelable>
            )
        } else {
            ""
        })
    )
}

/**
 * Instance convenience: compute metadata from this object's runtime type.
 */
inline fun <reified T : Any> T.metadata(): Map<String, Any> = T::class.metadata()
