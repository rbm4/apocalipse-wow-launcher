# jpackage WiX Resource Overrides

Place Windows installer customization files in this directory.

`jpackage --resource-dir` can override WiX resources such as:

- `MsiInstallerStrings_en.wxl` overrides jpackage's English installer strings.
- `MsiInstallerStrings_pt-BR.wxl` adds Brazilian Portuguese strings and the `pt-BR` MSI UI culture.
  Keep its string IDs in sync with the English file; `.github/scripts/Validate-MsiLocalization.ps1`
  checks the cultures and IDs before local or release builds.
- `main.wxs`, `ui.wxf`, and `overrides.wxi` for deeper WiX installer layout/behavior.
- Bitmap and icon resources referenced by custom WiX templates.

The MSI retains English and includes Portuguese (Brazil). Windows Installer selects the matching UI
language when that language is available on the user's Windows installation.

Keep overrides small and deliberate. Full WiX template overrides can break across JDK versions.
