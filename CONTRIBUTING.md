# Contributing to AuraPlayer

Thank you for your interest in contributing to AuraPlayer!

## Architecture Guidelines
- **Kotlin & Jetpack Compose**: Follow Material 3 design guidelines.
- **Audio Service**: Keep playback logic within MusicPlaybackService and ExoPlayer session management.
- **Offline & Privacy First**: AuraPlayer has zero trackers and zero cloud telemetry. All features must work completely offline.

## Pull Request Process
1. Fork the repo and create your branch from main.
2. Ensure your changes compile and pass tests:
   `ash
   ./gradlew test
   `
3. Submit your pull request with a descriptive title and summary.
