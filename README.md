# BIMO

A personal music player for Android, customized and maintained by **Rana**.

## About

BIMO is a personal customized fork of [OuterTune](https://github.com/OuterTune/OuterTune), which itself is a fork of [InnerTune](https://github.com/z-huang/InnerTune). I customized this project for my own personal use, including UI changes, branding, configuration, and additional improvements.

**BIMO is not affiliated with YouTube, Google LLC, or any of their affiliates and subsidiaries.**

## Features

- 🎵 **YouTube Music Integration** — Play, search, and save songs, videos, albums, and playlists from YouTube Music
- 📥 **Song Downloading** — Download songs for offline playback
- 🔇 **Background Playback** — AD-free background music playback
- 📚 **Integrated Library** — Modern library screen with multiple queues
- 🔄 **Account Sync** — YouTube Music account login with song, subscription, and album syncing
- 🎶 **Local Music** — Play local audio files (MP3, FLAC, OGG, M4A, and more)
- 📝 **Synchronized Lyrics** — Lyrics with karaoke-style word timing support
- 🎛️ **Audio Effects** — Normalization, tempo/pitch adjustment, equalizer
- 🚗 **Android Auto** — Full Android Auto support
- 🌙 **AMOLED Dark Mode** — Pure black theme for OLED displays
- 🎨 **Material You** — Dynamic color theming
- 📱 **Modern Design** — Material 3 UI with smooth animations

## Screenshots

*Coming soon*

## Installation

### Building from Source

1. Clone the repository
2. Open in Android Studio
3. Build the `coreDebug` variant:
   ```bash
   ./gradlew assembleCoreDebug
   ```

### Build Variants

| Variant | Update Checker | FFMpeg Extractor |
|---------|---------------|------------------|
| core    | ❌             | ❌                |
| full    | ❌ (disabled)  | ✅                |

## Community & Support

- **GitHub**: [https://github.com/Rana0069](https://github.com/Rana0069)
- **Discord**: [https://discord.gg/Kn6aJYcNQX](https://discord.gg/Kn6aJYcNQX)

Join the Discord community for support, updates, feedback, and discussion.

## Attribution & Credits

BIMO is built upon the excellent work of open-source contributors:

- **[OuterTune](https://github.com/OuterTune/OuterTune)** — The upstream project this fork is based on, created by DD3Boh and contributors
- **[InnerTune](https://github.com/z-huang/InnerTune)** — The original project by Zion Huang that OuterTune was forked from
- **[Musicolet](https://play.google.com/store/apps/details?id=in.krosbits.musicolet)** — Inspiration for local music player experience
- **[Gramophone](https://github.com/FoedusProgramme/Gramophone)** — Lyrics parser contributions

All upstream contributors, licenses, and attributions are preserved in this project. See the [Attribution screen](app/src/main/java/com/rana/bimo/ui/screens/settings/AttributionScreen.kt) within the app and the [open-source licenses](app/src/main/java/com/rana/bimo/ui/screens/settings) for full details.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

The upstream OuterTune and InnerTune projects are also licensed under GPL-3.0. All license requirements remain applicable to this derivative work.

> **Note**: I (Rana) did not write the original OuterTune or InnerTune codebase. My contributions are limited to personal customizations, UI modifications, branding, configuration changes, and improvements. The original developers and contributors retain credit for their work.

## Disclaimer

This project and its contents are not affiliated with, funded, authorized, endorsed by, or in any way associated with YouTube, Google LLC or any of its affiliates and subsidiaries.

Any trademark, service mark, trade name, or other intellectual property rights used in this project are owned by the respective owners.
