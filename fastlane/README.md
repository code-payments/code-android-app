fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android code_tests

```sh
[bundle exec] fastlane android code_tests
```

Runs all the tests for Code

### android fc_tests

```sh
[bundle exec] fastlane android fc_tests
```

Runs all the tests for Flipchat

### android deploy_code_internal

```sh
[bundle exec] fastlane android deploy_code_internal
```

Build and Deploy a new internal version of Code to the Google Play

### android deploy_fc

```sh
[bundle exec] fastlane android deploy_fc
```

Build and Deploy a new version of Flipchat to the Google Play Store

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
