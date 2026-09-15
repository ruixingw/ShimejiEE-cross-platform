# ShimejiEE Cross-Platform

[简体中文](README.zh_CN.md) | English

A cross-platform port of the Shimeji desktop mascots, based on
[Kilkakon's shimeji](https://kilkakon.com/shimeji/) and
[LavenderSnek/ShimejiEE-cross-platform](https://github.com/LavenderSnek/ShimejiEE-cross-platform).
This fork restores the macOS window interactions and the original settings/behaviour features, and ships a
complete Chinese translation.

> Done entirely by vibe coding using [GLM-5.3](https://z.ai) (Z.ai).

[![Release](https://img.shields.io/badge/release-v2.2.0-blue)](https://github.com/ruixingw/ShimejiEE-cross-platform/releases)

## Highlights

- **Real macOS window interactions**: mascots grab, carry and throw real windows, and *Restore Windows* in the tray
  brings thrown windows back; mascots respect the Dock and menu bar
- **Interactive windows whitelist/blacklist** like the original Windows version — mascots seek out matching windows
  even when they are not frontmost
- **Full settings window** (General / Interactive Windows / Window Mode / About): scaling in 0.1 steps, opacity,
  scaling filter (nearest / bicubic / hqx), every behaviour toggle, tray name override, ...
- **Per-behaviour toggles** from the mascot context menu for behaviours marked `Toggleable` (compatible with the
  original `DisabledBehaviours` format)
- **Window mode (sandbox)**: keep mascots inside a window with configurable size, background colour and background
  image (centre/fill/fit/stretch) — nice for streaming
- Pause/resume animations (globally and per mascot), multiscreen toggle, single instance protection, first-run
  credits splash, mascot statistics window
- Complete Simplified Chinese (plus Traditional Chinese and 20+ other languages)

## Platform support

| Platform | Status |
|---|---|
| macOS (Apple Silicon / Intel) | ✅ Primary, native rendering + window interactions |
| Windows | ⚠️ Inherited from upstream, lightly tested |
| Linux | ⚠️ Basic support, window interactions not implemented |

## Installation (macOS)

1. Download the `no-jre` build from [Releases](https://github.com/ruixingw/ShimejiEE-cross-platform/releases) and
   unzip it;
2. Install a **JDK 23 or newer** (Homebrew: `brew install openjdk`);
3. Run:

   ```bash
   cd ShimejiEE
   java -jar ShimejiEE.jar
   ```

4. Pick your mascots from the tray menu on first start (drop image sets into `img/`);
5. **Window interactions require the Accessibility permission**: System Settings → Privacy & Security →
   Accessibility → add the `java` binary running Shimeji. The app offers to open this when needed.

## Layout

```
ShimejiEE/
├── ShimejiEE.jar      main program
├── lib/               native libraries (leave alone)
├── conf/              configuration (incl. settings.properties)
├── img/               image sets (one folder each)
└── sound/             global sounds (optional)
```

Image sets can be downloaded from communities like
[the shimeji tag on DeviantArt](https://www.deviantart.com/tag/shimeji); drop them into `img/` and select them via
the tray's *Choose Shimeji*. Folders inside `img/unused/` are hidden.

## Building from source

Requires Python 3.13+, JDK 23+, Maven, CMake, Ninja and [jextract](https://jdk.java.net/jextract/).

```bash
python3 build.py --jextract <path-to-jextract>
```

The install folder is created at `build/ShimejiEE/`. See [docs/building.md](docs/building.md).

## Known limitations

- Multi-monitor setups are not thoroughly tested;
- Linux window interactions (X11/Wayland) are not implemented;
- The original NimROD theme editor was not ported.

## Credits

This project stands on the shoulders of:

- **Group Finity** (Yuki Yamada) — original Shimeji
  ([archived site](https://web.archive.org/web/20140530231026/http://www.group-finity.com/Shimeji/))
- **shimeji-ee Group** — internationalisation and many improvements
- **[Kilkakon](https://kilkakon.com/shimeji/)** — long term maintenance: sounds, affordances, japanese conf
  compatibility ([Discord](https://discord.gg/dcJGAn3))
- **[nonowarn](https://github.com/nonowarn/shimeji4mac)** — the initial macOS implementation
- **[TigerHix](https://github.com/TigerHix/shimeji-universal)** — 64 bit support for Windows
- **[LavenderSnek](https://github.com/LavenderSnek/ShimejiEE-cross-platform)** — the cross-platform fork and the
  panama native backend

The bundled [hqx-java](https://github.com/Arcnor/hqx-java) pixel scaler is LGPL-3 licensed.

## License

Same terms as upstream (zlib style: keep the attribution, mark your changes) — the full license chain is in
[LICENSE.md](LICENSE.md).
