# jpackage WiX Resource Overrides

Place Windows installer customization files in this directory.

`jpackage --resource-dir` can override WiX resources such as:

- `MsiInstallerStrings_en.wxl` provides the Brazilian Portuguese installer strings. jpackage
  requires this exact resource filename, while the file's `Culture="pt-BR"` selects the installer
  language. Keep all 13 jpackage string IDs; `.github/scripts/Validate-MsiLocalization.ps1` checks
  them before local or release builds.
- `main.wxs`, `ui.wxf`, and `overrides.wxi` for deeper WiX installer layout/behavior.
- Bitmap and icon resources referenced by custom WiX templates.

The MSI is linked for PT-BR only. Adding a second WXL culture to this jpackage build makes WiX link
the same localization IDs more than once, so additional cultures must be built as separate MSI
artifacts rather than added to this resource directory.

Keep overrides small and deliberate. Full WiX template overrides can break across JDK versions.
