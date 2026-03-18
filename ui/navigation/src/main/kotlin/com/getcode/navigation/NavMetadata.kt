package com.getcode.navigation

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.getcode.navigation.results.NavResultKey
import com.getcode.navigation.results.NavigationRetVal
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

enum class NavMetadataKeys(val key: String, ) {
    IsNonDismissable("non_dismissable"),
    IsSheet("sheet"),
    IsSolitarySheet("sheet_solitary"),
    NavResultKey("navresult_key"),
}

inline fun <reified T : NavKey> EntryProviderScope<NavKey>.annotatedEntry(noinline content: @Composable (T) -> Unit) {
    val metadata = T::class.metadata()
    return entry(metadata = metadata, content = content)
}

inline fun <reified T: Any> T.metadata(): Map<String, Any> {
    val retValType = T::class.supertypes.find { it.classifier == NavigationRetVal::class }
    val resultClass = retValType?.arguments?.firstOrNull()?.type?.classifier as? KClass<*>

    val metadata = mapOf(
        NavMetadataKeys.IsSheet.key to (this is Sheet),
        NavMetadataKeys.IsSolitarySheet.key to (SolitarySheet::class.java.isAssignableFrom(T::class.java)),
        NavMetadataKeys.IsNonDismissable.key to (NonDismissableRoute::class.java.isAssignableFrom(T::class.java)),
        NavMetadataKeys.NavResultKey.key to (if (NavigationRetVal::class.isSuperclassOf(T::class)) {
            @Suppress("UNCHECKED_CAST")
            (NavResultKey(
                T::class as KClass<NavigationRetVal<Parcelable>>,
                resultClass as KClass<Parcelable>
            ))
        } else {
            ""
        })
    )
    return metadata
}
