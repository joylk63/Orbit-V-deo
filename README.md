# Orbit V!deo

A Nothing OS-inspired Android media player built with Kotlin, Jetpack Compose, and AndroidX Media3 ExoPlayer.

## Initial features
- Dark minimal interface with lime accents
- Open local video and audio files through Android's document picker
- Media3 playback view and built-in playback controls
- Playback speed toggle (1x, 1.25x, 1.5x, 2x)
- GitHub Actions workflow to build a debug APK

## Build an APK
1. Open the **Actions** tab in this repository.
2. Select **Build Orbit V!deo APK**.
3. Run the workflow using **Run workflow**, or push a commit to main.
4. Open the completed workflow run and download the `orbit-video-debug-apk` artifact.

## Planned next
Custom media library, explicit audio-track selector, subtitle selection UI, playlist management, and gesture controls.

## Stack
- Kotlin
- Jetpack Compose + Material 3
- AndroidX Media3 ExoPlayer
- Gradle / GitHub Actions
