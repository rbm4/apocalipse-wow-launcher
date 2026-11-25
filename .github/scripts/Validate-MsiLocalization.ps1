[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$resourceDirectory = Join-Path $PSScriptRoot "..\..\src\jpackage\resources"
$englishPath = Join-Path $resourceDirectory "MsiInstallerStrings_en.wxl"
$portuguesePath = Join-Path $resourceDirectory "MsiInstallerStrings_pt-BR.wxl"

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

$englishIds = Read-WixLocalization -Path $englishPath -ExpectedCulture "en-us"
$portugueseIds = Read-WixLocalization -Path $portuguesePath -ExpectedCulture "pt-BR"
$missingIds = @($englishIds | Where-Object { -not $portugueseIds.Contains($_) })
$extraIds = @($portugueseIds | Where-Object { -not $englishIds.Contains($_) })

if ($missingIds.Count -gt 0 -or $extraIds.Count -gt 0) {
    $details = @()
    if ($missingIds.Count -gt 0) { $details += "missing: $($missingIds -join ', ')" }
    if ($extraIds.Count -gt 0) { $details += "unexpected: $($extraIds -join ', ')" }
    throw "PT-BR installer strings must match the English override ($($details -join '; '))."
}

Write-Host "Validated $($portugueseIds.Count) PT-BR WiX strings; jpackage will include the pt-BR MSI culture."
