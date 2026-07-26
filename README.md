# Apocalipse WoW Launcher

JavaFX launcher for synchronizing Apocalipse WoW client MPQ patches and starting the 3.3.5a client.

## Requirements

- JDK 25+ with `jpackage` available on `PATH` or under `JAVA_HOME/bin`.
- Maven 3.9+.
- Windows is required for building Windows `.exe` or `.msi` installers with `jpackage`.
- WiX Toolset is required by `jpackage` for Windows `.exe` and `.msi` installers.

## Development

Run the launcher:

```powershell
mvn javafx:run
```

Build the application jar and dependency input directory:

```powershell
mvn clean package
```

The packaging input is written to:

```text
target/launcher-input
```

## Native Packages

Build a portable Windows application image:

```powershell
mvn clean verify -Pjpackage-app-image
```

Build a Windows `.exe` installer:

```powershell
mvn clean verify -Pjpackage-exe
```

Build a Windows `.msi` installer:

```powershell
mvn clean verify -Pjpackage-msi
```

Outputs are written under:

```text
target/jpackage
```

Unsigned installers work, but Windows SmartScreen may warn users that the publisher is unknown.

If installer builds fail with `Can not find WiX tools`, install WiX Toolset and make sure `wix.exe`
or WiX v3 `candle.exe`/`light.exe` are on `PATH`. The `jpackage-app-image` profile does not need WiX.

## Release Workflow

MSI packaging is done locally on Windows because `jpackage --type msi` depends on Windows tooling.
Use the local release script to check dependencies, build the installer, and optionally publish a
GitHub release:

```powershell
.\build-local-msi.ps1
```

For the full interactive release flow:

```powershell
.\build-local-msi.ps1 -Release
```

The script can prompt to install missing local dependencies with Chocolatey or winget, prompt to bump
`pom.xml`, build the MSI, create a `launcher-v<project.version>` tag/release with GitHub CLI, and
upload the generated `.msi` as a release asset. Local release outputs are written to `.local-release/`,
which is ignored by Git.

## Current Launcher Flow

1. Select the WoW 3.3.5a folder.
2. The launcher writes `set realmlist wow.apocalipse.cloud`.
3. Fetch the fixed backend manifest.
4. Compare `Data/patch-A.MPQ` by SHA-256.
5. Download the backend MPQ artifact if missing or outdated.
6. Verify the download before installing it as `Data/patch-A.MPQ`.
7. Remove older launcher-managed `patch-apocalipse-*.mpq` files.
8. Unlock `Launch WoW` after verification succeeds.
9. Launch `Wow.exe`.

On first launch, the user is prompted to choose the WoW folder. Later changes are available through
the launcher `Options` button.

Default manifest:

```text
https://wow.apocalipse.cloud/patches/manifest.json
```
