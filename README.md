# MeloDrive

A minimal Android music player supporting local libraries and ad-free YouTube Music streaming. Designed for simplicity and compatibility with Android Auto.

## Features

- **Local Library**: Play local audio files (MP3, FLAC, M4A, etc.) from any folder.
- **YouTube Streaming**: Search and stream directly from YouTube Music without an account.
- **Android Auto**: Full integration with car displays and controls.
- **Background Playback**: Seamless media controls via notification, lock screen, and headsets.

## Building

Requires Android SDK (Min API 26).

```bash
git clone https://github.com/duartebranco/melodrive.git
cd melodrive
./gradlew assembleDebug
```

## Tech Stack

- Jetpack Compose (UI)
- Media3 ExoPlayer (Playback)
- NewPipeExtractor (YouTube streaming)
- Kotlin Coroutines & Flow (Async)
