# Verification: 2026-09-24

Built locally on Windows using Microsoft OpenJDK 17, Gradle 8.11.1,
Android SDK 35 and Build Tools 35.0.0.

Passed:

- `:app:testDebugUnitTest`: 3 tests, 0 failures, 0 errors.
- `:app:lintDebug`: 0 errors, 7 warnings. Remaining warnings concern backup rules,
  Chinese UI string formatting, launcher icon resource conventions, and the lint ImplicitSamInstance false positive
  on explicit Intent arguments to Context.stopService (which uses intent matching,
  not object identity).
- `:app:assembleDebug`: debug APK generated successfully.
- `apksigner verify --verbose`: APK signature verified (v2).
- APK contains both keyword lists, model encoder/decoder/joiner, token vocabulary,
  and arm64-v8a / armeabi-v7a / x86_64 native libraries.
- Merged manifest includes microphone/foreground-service/notification permissions;
  no INTERNET permission. No Baidu SDK or WakeUp.bin assets are included.
- Official Gradle distribution SHA-256 checked; SDK/model SHA-256 pinned in assets.lock.json.

Deliverable: `dist/short-video-kws-0.1.0-debug.apk`, with adjacent SHA-256 file.
Uses a development signing key and is intended for testing, not a store release.

No Android device was connected. Not yet validated on a phone:

- Recognition accuracy and latency for all eight command keywords.
- Background recording under the phone manufacturer's battery policy.
- Playback audio false triggers, and the effect of headphones/prefixed commands.
- Gesture coordinates on current Douyin, especially the configurable comment button.
- Stop notification, lockscreen stop, permission revocation and rapid restart on device.

The unit tests exercise command allowlisting, stale callbacks, duplicate suppression,
and busy/stopped rejection. They do not measure acoustic accuracy or prove UI outcomes.
