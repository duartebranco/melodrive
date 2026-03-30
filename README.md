# MeloDrive

Free Android music player with YouTube Music streaming and local library support. Works with Android Auto.

## Features

- **Local library** — pick any folder, plays MP3, FLAC, OGG, M4A, AAC, WAV. Browse by song or album.
- **YouTube Music streaming** — search and stream any song via [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor). No account, no API key, no ads.
- **Android Auto** — appears in the car's media list, fully browsable and controllable from the head unit.
- **Media controls everywhere** — steering wheel buttons, headphones, lock screen, and notification all work through the same `MediaSessionCompat`.
- **Cover art** — embedded ID3/FLAC artwork for local files; YouTube Music thumbnails for streamed tracks. Shown in the now-playing screen, notification, and car display.

## Build

1. Clone the repo.
2. Create `local.properties` with your Android SDK path:
   ```
   sdk.dir=/path/to/android/sdk
   ```
3. Run `./gradlew assembleDebug`.

Minimum API 26 (Android 8.0). Tested up to API 35.

## Tech stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material3 |
| Playback | Media3 ExoPlayer 1.5.0 |
| Android Auto | `MediaBrowserServiceCompat` + `MediaSessionCompat` |
| YouTube extraction | NewPipeExtractor 0.26.0 (music.youtube.com) |
| Image loading | Coil 2.7.0 |
| HTTP | OkHttp 4.12.0 |

## How YouTube streaming works

MeloDrive uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) — the same library that powers the NewPipe app — to talk directly to `music.youtube.com`. No YouTube Data API, no account required.

When you search, `SearchInfo.getInfo()` queries the YouTube Music search endpoint with the `MUSIC_SONGS` filter, so results are actual songs (not videos). When you tap a result, `StreamInfo.getInfo()` fetches the page and extracts a direct audio stream URL. ExoPlayer then streams that URL. The stream URL is resolved at play time and is never stored.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | YouTube streaming |
| `READ_MEDIA_AUDIO` | Local file access (API 33+) |
| `READ_EXTERNAL_STORAGE` | Local file access (API < 33) |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Playback notification |

## Notes

- YouTube stream URLs expire after a few hours. If a track stops playing, tap it again to re-resolve.
- Local folder access is persisted via `takePersistableUriPermission`, so the library survives app restarts without re-picking the folder.
