[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$resourceDirectory = Join-Path $PSScriptRoot "..\..\src\jpackage\resources"
$localizationPath = Join-Path $resourceDirectory "MsiInstallerStrings_en.wxl"
$additionalLocalizationFiles = @(Get-ChildItem -LiteralPath $resourceDirectory -Filter "*.wxl" -File | Where-Object Name -ne "MsiInstallerStrings_en.wxl")
if ($additionalLocalizationFiles.Count -gt 0) {
    throw "The MSI must use one PT-BR culture file to avoid duplicate WiX localization IDs. Unexpected files: $($additionalLocalizationFiles.Name -join ', ')"
}

function Read-WixLocalization {
    param(
        [Parameter(Mandatory)]
        [string]$Path,
        [Parameter(Mandatory)]
        [string]$ExpectedCulture
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required WiX localization file was not found: $Path"
    }

    [xml]$document = Get-Content -LiteralPath $Path -Raw
    $root = $document.DocumentElement
    if ($root.LocalName -ne "WixLocalization" -or $root.GetAttribute("Culture") -ne $ExpectedCulture) {
        throw "$Path must declare WixLocalization Culture='$ExpectedCulture'."
    }

    $ids = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($stringNode in $document.SelectNodes("//*[local-name()='String']")) {
        $id = $stringNode.GetAttribute("Id")
        if ([string]::IsNullOrWhiteSpace($id) -or [string]::IsNullOrWhiteSpace($stringNode.InnerText)) {
            throw "$Path contains a string with an empty Id or value."
        }
        if (-not $ids.Add($id)) {
            throw "$Path contains duplicate string Id '$id'."
        }
    }

    if ($ids.Count -eq 0) {
        throw "$Path does not contain any WiX strings."
    }

    return $ids
}

$requiredIds = @(
    "MainFeatureTitle",
    "DowngradeErrorMessage",
    "DisallowUpgradeErrorMessage",
    "ShortcutPromptDlg_Title",
    "ShortcutPromptDlgTitle",
    "ShortcutPromptDlgBannerBitmap",
    "ShortcutPromptDlgDescription",
    "ShortcutPromptDlgDesktopShortcutControlLabel",
    "ShortcutPromptDlgStartMenuShortcutControlLabel",
    "InstallDirNotEmptyDlg_Title",
    "InstallDirNotEmptyDlgInstallDirExistMessage",
    "ContextMenuCommandLabel",
    "OsConditionMessage"
)
$localizedIds = Read-WixLocalization -Path $localizationPath -ExpectedCulture "pt-BR"
$missingIds = @($requiredIds | Where-Object { -not $localizedIds.Contains($_) })
$extraIds = @($localizedIds | Where-Object { $_ -notin $requiredIds })

if ($missingIds.Count -gt 0 -or $extraIds.Count -gt 0) {
    $details = @()
    if ($missingIds.Count -gt 0) { $details += "missing: $($missingIds -join ', ')" }
    if ($extraIds.Count -gt 0) { $details += "unexpected: $($extraIds -join ', ')" }
    throw "PT-BR installer strings are incomplete ($($details -join '; '))."
}

Write-Host "Validated $($localizedIds.Count) PT-BR WiX strings; jpackage will link a single pt-BR MSI culture."
