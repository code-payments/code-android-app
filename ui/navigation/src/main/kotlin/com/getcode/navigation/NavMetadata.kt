package com.getcode.navigation

import android.os.Parcelable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.getcode.navigation.results.NavResultKey
import com.getcode.navigation.results.NavigationRetVal
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass

enum class NavMetadataKeys(val key: String, ) {
    IsNonDismissable("non_dismissable"),
    IsNonDraggable("non_draggable"),
    IsSheet("sheet"),
    IsWrapContentSheet("sheet_wrap_content"),
    IsHalfSheet("sheet_half"),
    IsSolitarySheet("sheet_solitary"),
    NavResultKey("navresult_key"),
}

/**
 * DSL helper: registers an entry whose metadata is derived from [T]'s marker interfaces.
 *
 * Every destination is wrapped in a [Box] tagged with a stable screen-root id so the whole
 * screen is addressable as a single resource-id in UI tests (Maestro / UiAutomator, via
 * `testTagsAsResourceId`). The tag defaults to one derived from the route type name
 * ([screenRootTag], e.g. `AppRoute.Menu.MyAccount` → `my_account_screen`); pass an explicit
 * [testTag] only when a route needs an id that differs from its type name.
 *
 * Keeping the tag here — at the one place every route is registered — means screen-root
 * test anchors live in a single file and can't drift out of sync with the screens.
 */
inline fun <reified T : NavKey> EntryProviderScope<NavKey>.annotatedEntry(
    testTag: String? = null,
    noinline content: @Composable (T) -> Unit
) {
    val resolvedTag = testTag ?: screenRootTag(T::class.simpleName)
    val tagged: @Composable (T) -> Unit = { key -> Box(Modifier.testTag(resolvedTag)) { content(key) } }
    entry(metadata = T::class.metadata(), content = tagged)
}

/**
 * Derives a screen-root test id from a route's simple type name: CamelCase becomes
 * snake_case with a `_screen` suffix (e.g. `MyAccount` → `my_account_screen`,
 * `Scanner` → `scanner_screen`).
 */
fun screenRootTag(simpleName: String?): String {
    val base = (simpleName ?: "unknown")
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .lowercase()
    return "${base}_screen"
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
        NavMetadataKeys.IsHalfSheet.key to HalfSheet::class.java.isAssignableFrom(this.java),
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
