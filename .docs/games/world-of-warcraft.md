# World of Warcraft integration

## Setup and settings

On a fresh start, or when the saved folder no longer contains the configured executable, the launcher opens **Options** and offers to download the client or select an existing installation. It checks for at least 17 GiB of available space before starting a client download. Client files come from the bundled `WoWBrasil.torrent`. When installation succeeds, the launcher saves the client folder and starts the update-and-play flow.

User settings live in `%LOCALAPPDATA%\ApocalipseWoWLauncher\launcher.properties`, with a fallback to `.apocalipse-wow-launcher` in the user's home directory. The same directory holds startup logs, persistent website cookies, and torrent session data. Bundled endpoints, the executable name, and the fixed torrent peer are defined in [launcher.properties](../../src/main/resources/launcher.properties). Patch synchronization uses the bundled manifest URL.

## Update and play

1. Validate the selected client folder and write `set realmlist wow.apocalipse.cloud` to the first existing realmlist candidate. If no candidate exists, create `Data/enUS/realmlist.wtf`.
2. Fetch the backend manifest. For a nonempty manifest, use the first file entry and compare the SHA-256 with the local `Data/patch-A.MPQ`.
3. When needed, download to a temporary file, verify the hash, and move the file into `Data/patch-A.MPQ`. Remove matching legacy `patch-apocalipse-*.mpq` files after synchronization.
4. Launch with the selected profile after synchronization succeeds. The launch service deletes the client's `Cache` directory before starting `Wow.exe`.

The backend publishes the MPQ. The launcher consumes the manifest and artifact. The home tab also fetches news and refreshes realm status every 30 seconds. The Site tab opens the community website in a JavaFX web view.

The home tab uses the same status card layout as Project Zomboid, with a 320 × 200 area below the status heading.
