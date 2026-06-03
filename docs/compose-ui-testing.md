# Compose UI Testing Guide

This project uses **Robolectric + Compose UI Test** to run Compose screen tests as local JVM unit tests — no emulator or device required.

## Quick start

### 1. Add the dependency bundle

```kotlin
// build.gradle.kts
dependencies {
    testImplementation(libs.bundles.unit.testing)
    testImplementation(libs.bundles.compose.ui.testing)  // adds Robolectric
    testImplementation(project(":libs:test-utils"))
}
```

The `unit-testing` bundle already includes `ui-test-junit4` and `ui-test-manifest`. The `compose-ui-testing` bundle adds Robolectric. The convention plugin (`flipcash.android.library`) configures `isIncludeAndroidResources = true` globally.

A global `robolectric.properties` in `:libs:test-utils` pins SDK 35, so you don't need `@Config(sdk = [...])` on each class.

### 2. Write a test

```kotlin
@RunWith(RobolectricTestRunner::class)
class MyScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `displays expected text`() {
        composeTestRule.setContent {
            DesignSystem {
                MyScreen(/* ... */)
            }
        }

        composeTestRule.onNodeWithText("Expected").assertIsDisplayed()
    }
}
```

### 3. Run

```bash
./gradlew :your:module:test
```

## Pattern: Interface + Fake

For screens that depend on a ViewModel or delegate, extract an interface for the screen-facing API and test against a fake. This avoids heavy DI/mock setup.

**Interface** — captures what the screen reads and writes:

```kotlin
interface MyScreenController {
    val state: StateFlow<MyState>
    fun onAction()
}
```

**Production** — ViewModel or delegate implements it:

```kotlin
class MyViewModel : ViewModel(), MyScreenController {
    override val state = MutableStateFlow(MyState())
    override fun onAction() { /* ... */ }
}
```

**Screen** — takes the interface:

```kotlin
@Composable
fun MyScreen(controller: MyScreenController) {
    val state by controller.state.collectAsStateWithLifecycle()
    // ...
}
```

**Fake** — trivial test double:

```kotlin
class FakeMyScreenController(
    override val state: MutableStateFlow<MyState> = MutableStateFlow(MyState()),
) : MyScreenController {
    var actionCount = 0
    override fun onAction() { actionCount++ }
}
```

**Test** — no mocks, no DI:

```kotlin
@Test
fun `tapping button triggers action`() {
    val controller = FakeMyScreenController()
    composeTestRule.setContent {
        DesignSystem { MyScreen(controller) }
    }

    composeTestRule.onNodeWithText("Do it").performClick()
    assertEquals(1, controller.actionCount)
}
```

## Key APIs

| API | Purpose |
|---|---|
| `onNodeWithText("...")` | Find node by visible text |
| `onNodeWithContentDescription("...")` | Find by accessibility label |
| `assertIsDisplayed()` | Assert node is visible |
| `assertIsEnabled()` / `assertIsNotEnabled()` | Assert enabled state |
| `performClick()` | Simulate tap |
| `performTextInput("...")` | Type text |
| `waitForIdle()` | Wait for recomposition after state changes |

## Tips

- **Wrap with `DesignSystem { }`** — provides the app's theme, colors, typography, and dimensions.
- **Use `MutableStateFlow` in fakes** — update `.value` to simulate state changes, then call `composeTestRule.waitForIdle()`.
- **Loading spinners hide text** — `CodeButton` replaces text with a spinner during loading. Find the button by test tag or verify behavior after loading completes.
- **First run downloads Robolectric JARs** — subsequent runs are cached.
- **Keep tests in `src/test/`** — these are local JVM tests, not `androidTest`.
