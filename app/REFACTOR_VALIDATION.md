# Android refactoring validation

Baseline: frontend commit `5c7b7de1512d9d7290d1c09274ef68acb441f038`.
Worktree: `frontend-android-refactor` under the capstone copies directory.

## Scope

Changes are confined to the phone application (`app/`). The Wear OS module,
phone-side `wear` integration package, backend, API routes, and route scoring
weights were not changed. Existing member-ID guardian registration remains.

## Changes

- Shared authenticated backend transport for account and guardian/notification APIs.
- Separate authentication bootstrap, main screen, foreground location observer,
  home UI components, detection settings UI, and route selection calculation.
- Guardian and profile ViewModels own request state and account-change cleanup.
- Removed unreachable legacy invite dialog from the main screen.
- Added an HTTP contract test for guardian and notification requests.

## Automated verification

- `:app:assembleDebug`: successful.
- `:app:compileDebugUnitTestKotlin`: successful.
- Direct JUnit execution: 115 tests run, 114 passed, 0 failures, 1 assumption skip.
- The skipped test is `LiveRouteValidationTest`; live external route validation
  requires explicit opt-in and was not enabled.
- Gradle's standard test worker still has the previously observed test-class
  loading failure. Direct JUnit uses the resolved debug-test classpath. This is
  a verification workaround, not a fix to the Gradle test task.
- `git diff --check`: successful.

## Physical-device verification

The refactored debug APK was installed using an update installation on the
connected SM_F971N. Existing authentication and app data were retained.

- Main activity and home screen opened successfully.
- Guardian-management screen loaded the authenticated member ID and an empty list.
- Refresh did not display an error; own risk history returned an empty-list dialog.
- Profile screen displayed server account information.
- Name-edit dialog opened and was cancelled without saving.
- Settings screen opened and returned without changing settings.
- Force-stop followed by a new launch returned to the home screen.
- No AndroidRuntime fatal error was observed for the new app process.

## Not verified on the device

- Fresh Google/SMS signup and account switching.
- Two-account guardian creation, acceptance, rejection, revocation and permissions.
- Saving a new profile name or withdrawing an account.
- SOS delivery and FCM reception on a second device.
- Live route search, GPS accuracy, location updates while moving, and rotation.

No SOS was sent and no account or guardian data was mutated during these checks.
This refactoring does not complete the previously missing production safe-return,
WebSocket location-sharing, risk-creation or media features.

Changes have not been committed or uploaded to GitHub. The original working
directory is preserved separately.
