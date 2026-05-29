package com.getcode.navigation

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.getcode.navigation.results.NavResultKey
import com.getcode.navigation.results.NavigationRetVal
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.LocalOuterCodeNavigator
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass

enum class NavMetadataKeys(val key: String, ) {
    IsNonDismissable("non_dismissable"),
    IsNonDraggable("non_draggable"),
    IsSheet("sheet"),
    IsWrapContentSheet("sheet_wrap_content"),
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
 * Like [annotatedEntry] but re-provides the outer app-level [CodeNavigator][com.getcode.navigation.core.CodeNavigator]
 * as [LocalCodeNavigator] inside [content]. Use this for flow steps that need to push routes onto the
 * outer/app nav graph (e.g. region selection) while running inside a [FlowHost][com.getcode.navigation.flow.FlowHost].
 */
inline fun <reified T : NavKey> EntryProviderScope<NavKey>.flowAnnotatedEntry(
    noinline content: @Composable (T) -> Unit
) {
    entry(metadata = T::class.metadata()) { step: T ->
        val outerNavigator = LocalOuterCodeNavigator.current
        CompositionLocalProvider(LocalCodeNavigator provides outerNavigator) {
            content(step)
        }
    }
}

/**
 * Compute metadata from a [KClass] by inspecting its marker interfaces.
 *
 * Uses Java reflection instead of Kotlin reflection (`allSupertypes`) to walk
 * the type hierarchy. Kotlin reflection requires `@Metadata` annotations on
 * every supertype in the chain; R8 strips those from library classes like
 * `NavKey`, causing [KotlinReflectionNotSupportedError] in release builds.
 */
fun KClass<*>.metadata(): Map<String, Any> {
    val resultClass = findNavigationRetValTypeArg(this.java)

    return mapOf(
        NavMetadataKeys.IsSheet.key to Sheet::class.java.isAssignableFrom(this.java),
        NavMetadataKeys.IsWrapContentSheet.key to WrapContentSheet::class.java.isAssignableFrom(this.java),
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
 * Walk the generic interface/superclass hierarchy via Java reflection to find
 * `NavigationRetVal<T>` and return `T`'s [KClass], or null if not found.
 */
private fun findNavigationRetValTypeArg(cls: Class<*>): KClass<*>? {
    val targetName = NavigationRetVal::class.java.name
    val queue = ArrayDeque<java.lang.reflect.Type>()
    queue.addAll(cls.genericInterfaces)
    cls.genericSuperclass?.let { queue.add(it) }

    while (queue.isNotEmpty()) {
        val type = queue.removeFirst()
        if (type is ParameterizedType) {
            val rawType = type.rawType as? Class<*> ?: continue
            if (rawType.name == targetName) {
                return (type.actualTypeArguments.firstOrNull() as? Class<*>)?.kotlin
            }
            queue.addAll(rawType.genericInterfaces)
            rawType.genericSuperclass?.let { queue.add(it) }
        } else if (type is Class<*>) {
            queue.addAll(type.genericInterfaces)
            type.genericSuperclass?.let { queue.add(it) }
        }
    }
    return null
}

/**
 * Instance convenience: compute metadata from this object's runtime type.
 */
inline fun <reified T : Any> T.metadata(): Map<String, Any> = T::class.metadata()
