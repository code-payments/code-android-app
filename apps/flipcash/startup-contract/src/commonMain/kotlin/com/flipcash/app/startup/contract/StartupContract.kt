package com.flipcash.app.startup.contract

/**
 * The wiring the wallet's balance path needs before its first screen, written down once so the
 * Android and desktop wiring tests check the same list.
 *
 * Desktop rebuilds by hand what Android gets from Hilt modules, androidx-startup initializers and
 * `FlipcashApp.onCreate`. Each piece it missed in the spike (the token coordinator's listener
 * binding, the eager event repository, the curve tables, the network image loader) left the wallet
 * on a spinner or blank logos with no error. Here, Android's side has to classify every startup
 * entry point it has, and desktop has to prove each mirrored step took effect.
 */
object StartupContract {

    /** A piece of startup work desktop has to repeat. The desktop test checks each one's effect. */
    enum class Step {
        /** The event repository exists, so `Events.OnLoggedIn` reaches the session listeners. */
        EventRepository,

        /** The discrete curve tables are loaded; every balance valuation throws without them. */
        CurveTables,

        /** The image loader can fetch https URLs, which is where token logos live. */
        NetworkImageLoader,
    }

    sealed interface Desktop {
        data class Mirrored(val step: Step) : Desktop
        data class NotOnDesktop(val reason: String) : Desktop
    }

    /** `SessionListener` implementations desktop must bind as well. Fully qualified class names. */
    val sharedSessionListeners: Set<String> = setOf(
        "com.flipcash.app.tokens.TokenCoordinator",
    )

    /** `SessionListener` implementations only Android binds, with why desktop goes without. */
    val androidOnlySessionListeners: Map<String, String> = mapOf(
        "com.flipcash.shared.chat.internal.RealChatCoordinator" to "chat is not on desktop",
        "com.flipcash.app.contacts.ContactCoordinator" to "contacts are not on desktop",
    )

    /**
     * Every startup entry point the Android app declares: the androidx-startup initializers in its
     * manifest, the types `FlipcashApp` injects, and `SingletonImageLoader.Factory`, which it
     * implements. Keyed by fully qualified name.
     */
    val androidStartup: Map<String, Desktop> = mapOf(
        // androidx-startup initializers
        "com.flipcash.app.internal.startup.TraceInitializer" to
            Desktop.NotOnDesktop("trace export and Bugsnag breadcrumbs are Android tooling"),
        "com.flipcash.app.internal.startup.ClockDriftInitializer" to
            Desktop.NotOnDesktop("diagnostic only: records SNTP drift for the trace header"),
        "com.flipcash.app.internal.startup.FirebaseInitializer" to
            Desktop.NotOnDesktop("Firebase is Android-only"),
        "com.flipcash.app.internal.startup.LibSodiumInitializer" to
            Desktop.NotOnDesktop("the balance path makes no libsodium call; chat crypto does, and chat is not on desktop"),
        "com.flipcash.app.internal.startup.MnemonicCacheInitializer" to
            Desktop.NotOnDesktop("preloads the BIP39 word list; it loads on first use otherwise"),
        "com.flipcash.app.internal.startup.DiscreteBondingCurveInitializer" to
            Desktop.Mirrored(Step.CurveTables),
        "com.flipcash.app.internal.startup.CameraXInitializer" to
            Desktop.NotOnDesktop("no camera on desktop"),

        // FlipcashApp's injected dependencies
        "com.flipcash.app.auth.AuthManager" to
            Desktop.NotOnDesktop("restores the stored session; desktop signs in through DevSession until Keychain storage lands"),
        "com.getcode.opencode.repositories.EventRepository" to
            Desktop.Mirrored(Step.EventRepository),
        "com.flipcash.app.currency.PreferredCurrencyController" to
            Desktop.NotOnDesktop("the exchange falls back to the locale's currency"),
        "com.flipcash.shared.chat.media.ChatPhotoFetcher\$Factory" to
            Desktop.NotOnDesktop("chat is not on desktop"),
        "com.flipcash.app.messenger.LinkPreviewStartup" to
            Desktop.NotOnDesktop("link previews belong to chat"),
        "androidx.hilt.work.HiltWorkerFactory" to
            Desktop.NotOnDesktop("WorkManager is Android-only"),
        "com.flipcash.app.core.android.ActivityProvider" to
            Desktop.NotOnDesktop("tracks the foreground Activity"),
        "com.flipcash.app.bills.share.TipCodePreviewCache" to
            Desktop.NotOnDesktop("tip-code share previews are not on desktop"),

        // FlipcashApp implements this to build Coil's singleton loader
        "coil3.SingletonImageLoader\$Factory" to
            Desktop.Mirrored(Step.NetworkImageLoader),
    )
}
