[CmdletBinding(PositionalBinding = $false, DefaultParameterSetName = "Local")]
param(
    [Parameter(ParameterSetName = "Local")]
    [switch]$Release,
    [Parameter(Mandatory, ParameterSetName = "BuildOnly")]
    [switch]$BuildOnly,
    [string]$Version,
    [string]$SigningCertificateThumbprint,
    [string]$TimestampUrl = "http://timestamp.digicert.com",
    [switch]$SkipDependencyInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Prompt-YesNo {
    param(
        [string]$Question,
        [bool]$DefaultYes = $false
    )

    $suffix = if ($DefaultYes) { "[Y/n]" } else { "[y/N]" }
    $answer = Read-Host "$Question $suffix"
    if ([string]::IsNullOrWhiteSpace($answer)) {
        return $DefaultYes
    }
    return $answer.Trim().ToLowerInvariant().StartsWith("y")
}

function Get-ProjectVersion {
    [xml]$pom = Get-Content -LiteralPath "pom.xml"
    $ns = New-Object System.Xml.XmlNamespaceManager($pom.NameTable)
    $ns.AddNamespace("m", "http://maven.apache.org/POM/4.0.0")
    return $pom.SelectSingleNode("/m:project/m:properties/m:revision", $ns).InnerText
}

function Assert-ProjectVersion {
    param(
        [string]$Value,
        [string]$Source
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "$Source cannot be empty."
    }
    if ($Value.Trim().StartsWith("-")) {
        throw "$Source '$Value' looks like a command-line option. Use -Version 1.2.3 for a release version."
    }
    if ($Value -notmatch '^(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,4})$' -or
            [int]$Matches[1] -gt 255 -or [int]$Matches[2] -gt 255 -or [int]$Matches[3] -gt 65535) {
        throw "$Source '$Value' must use three Windows Installer version fields. Use values like 0.1.8 or 1.0.0."
    }
}

function Invoke-LoggedCommand {
    param(
        [string]$LogPath,
        [string]$FilePath,
        [string[]]$Arguments
    )

    Write-Host "Running: $FilePath $($Arguments -join ' ')"
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $exitCode = 1
    try {
        & $FilePath @Arguments 2>&1 | ForEach-Object { "$_" } | Tee-Object -FilePath $LogPath
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "$FilePath failed with exit code $exitCode. See $LogPath"
    }
}

function Invoke-NativeQuiet {
    param(
        [string]$FilePath,
        [string[]]$Arguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $FilePath @Arguments *> $null
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function New-WindowsIcon {
    param(
        [string]$SourcePath,
        [string]$DestinationPath
    )

    $resolvedSource = Resolve-Path -LiteralPath $SourcePath
    $destinationDirectory = Split-Path -Parent $DestinationPath
    New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null

    $pngBytes = [System.IO.File]::ReadAllBytes($resolvedSource)
    $fileStream = [System.IO.File]::Open($DestinationPath, [System.IO.FileMode]::Create)
    $writer = New-Object System.IO.BinaryWriter($fileStream)
    try {
        $writer.Write([UInt16]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]1)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]32)
        $writer.Write([UInt32]$pngBytes.Length)
        $writer.Write([UInt32]22)
        $writer.Write($pngBytes)
    } finally {
        $writer.Dispose()
        $fileStream.Dispose()
    }
}

function Find-WixBin {
    $light = Get-Command "light.exe" -ErrorAction SilentlyContinue
    $candle = Get-Command "candle.exe" -ErrorAction SilentlyContinue
    if ($light -and $candle) {
        return Split-Path -Parent $light.Source
    }

    $wix = Get-Command "wix.exe" -ErrorAction SilentlyContinue
    if ($wix) {
        return Split-Path -Parent $wix.Source
    }

    $roots = @()
    if (${env:ProgramFiles(x86)}) {
        $roots += ${env:ProgramFiles(x86)}
    }
    if (${env:ProgramFiles}) {
        $roots += ${env:ProgramFiles}
    }

    $toolset = Get-ChildItem -Path $roots -Directory -Filter "WiX Toolset v*" -ErrorAction SilentlyContinue |
        Sort-Object -Property Name -Descending |
        Select-Object -First 1
    if ($toolset) {
        $bin = Join-Path $toolset.FullName "bin"
        if ((Test-Path (Join-Path $bin "light.exe")) -and (Test-Path (Join-Path $bin "candle.exe"))) {
            return $bin
        }
    }

    return $null
}

function Install-LocalPackage {
    param(
        [string]$ToolName,
        [string]$ChocoPackage,
        [string]$WingetPackage,
        [bool]$SkipInstall
    )

    if ($SkipInstall) {
        return $false
    }

    $choco = Get-Command "choco.exe" -ErrorAction SilentlyContinue
    if ($choco -and (Prompt-YesNo "$ToolName was not found. Install $ChocoPackage with Chocolatey now?")) {
        & $choco.Source install $ChocoPackage -y --no-progress
        if ($LASTEXITCODE -ne 0) {
            throw "Chocolatey failed to install $ChocoPackage. Try running this PowerShell as Administrator."
        }
        return $true
    }

    $winget = Get-Command "winget.exe" -ErrorAction SilentlyContinue
    if ($winget -and (Prompt-YesNo "$ToolName was not found. Install $WingetPackage with winget now?")) {
        & $winget.Source install --id $WingetPackage -e --accept-package-agreements --accept-source-agreements
        if ($LASTEXITCODE -ne 0) {
            throw "winget failed to install $WingetPackage."
        }
        return $true
    }

    return $false
}

function Ensure-Command {
    param(
        [string]$CommandName,
        [string]$InstallHint,
        [string]$ChocoPackage,
        [string]$WingetPackage,
        [bool]$SkipInstall
    )

    if (Get-Command $CommandName -ErrorAction SilentlyContinue) {
        return
    }

    if ($ChocoPackage -and $WingetPackage) {
        [void](Install-LocalPackage `
            -ToolName $CommandName `
            -ChocoPackage $ChocoPackage `
            -WingetPackage $WingetPackage `
            -SkipInstall:$SkipInstall)
    }

    if (Get-Command $CommandName -ErrorAction SilentlyContinue) {
        return
    }

    throw "$CommandName was not found. $InstallHint"
}

function Ensure-Wix {
    param([bool]$SkipInstall)

    $wixBin = Find-WixBin
    if ($wixBin) {
        $env:Path = "$wixBin;$env:Path"
        Write-Host "WiX found: $wixBin"
        return
    }

    if ($SkipInstall) {
        throw "WiX was not found. Install WiX Toolset 3.x and add its bin directory to PATH."
    }

    $installed = Install-LocalPackage `
        -ToolName "WiX" `
        -ChocoPackage "wixtoolset" `
        -WingetPackage "WiXToolset.WiXToolset" `
        -SkipInstall:$SkipInstall
    if (-not $installed) {
        throw "WiX is required to build an MSI."
    }

    $wixBin = Find-WixBin
    if (-not $wixBin) {
        throw "WiX install finished, but light.exe/candle.exe were still not found."
    }

    $env:Path = "$wixBin;$env:Path"
    Write-Host "WiX installed: $wixBin"
}

function Set-ProjectVersion {
    param([string]$NewVersion)

    Assert-ProjectVersion -Value $NewVersion -Source "Version"

    Invoke-LoggedCommand `
        -LogPath (Join-Path ".local-release\logs" "version-set.log") `
        -FilePath "mvn" `
        -Arguments @("--batch-mode", "--no-transfer-progress", "versions:set-property", "-Dproperty=revision", "-DnewVersion=$NewVersion", "-DgenerateBackupPoms=false")
}

function Publish-Release {
    param(
        [string]$TagName,
        [string]$ProjectVersion,
        [string]$MsiPath
    )

    Ensure-Command "git" "Install Git for Windows." "git" "Git.Git" $SkipDependencyInstall
    Ensure-Command "gh" "Install GitHub CLI and run: gh auth login" "gh" "GitHub.cli" $SkipDependencyInstall

    & gh auth status
    if ($LASTEXITCODE -ne 0) {
        throw "GitHub CLI is not authenticated. Run: gh auth login"
    }

    $pomChanged = $false
    if ((Invoke-NativeQuiet "git" @("diff", "--quiet", "--", "pom.xml")) -ne 0) {
        $pomChanged = $true
    }

    if ($pomChanged -and (Prompt-YesNo "pom.xml changed. Commit the version bump before tagging?" $true)) {
        & git add pom.xml
        if ($LASTEXITCODE -ne 0) {
            throw "git add pom.xml failed."
        }
        & git commit -m "Bump launcher version to $ProjectVersion"
        if ($LASTEXITCODE -ne 0) {
            throw "git commit failed."
        }
    }

    $status = & git status --porcelain
    if ($status) {
        Write-Host "There are uncommitted changes:"
        $status | ForEach-Object { Write-Host $_ }
        if (-not (Prompt-YesNo "Continue creating the release from the current HEAD anyway?")) {
            throw "Release cancelled."
        }
    }

    $remote = (& git remote get-url origin).Trim()
    Write-Host "Origin: $remote"

    if (Prompt-YesNo "Push the current branch before creating the release?" $true) {
        & git push
        if ($LASTEXITCODE -ne 0) {
            throw "git push failed."
        }
    }

    $existingTag = & git tag --list $TagName
    if (-not $existingTag) {
        & git tag -a $TagName -m "Apocalipse WoW Launcher $ProjectVersion"
        if ($LASTEXITCODE -ne 0) {
            throw "git tag failed."
        }
    }

    if (Prompt-YesNo "Push tag ${TagName}?" $true) {
        & git push origin $TagName
        if ($LASTEXITCODE -ne 0) {
            throw "git push origin $TagName failed."
        }
    }

    if ((Invoke-NativeQuiet "gh" @("release", "view", $TagName)) -eq 0) {
        $existingRelease = & gh release view $TagName --json isDraft | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0 -or -not $existingRelease.isDraft) {
            throw "Release $TagName is already published. Build with a new version."
        }
        & gh release upload $TagName $MsiPath --clobber
        if ($LASTEXITCODE -ne 0) {
            throw "gh release upload failed."
        }
    } else {
        & gh release create $TagName $MsiPath `
            --verify-tag --draft `
            --title "Apocalipse WoW Launcher $ProjectVersion" `
            --notes "Windows MSI installer for Apocalipse WoW Launcher $ProjectVersion."
        if ($LASTEXITCODE -ne 0) {
            throw "gh release create failed."
        }
    }

    & gh release edit $TagName --draft=false --latest
    if ($LASTEXITCODE -ne 0) {
        throw "gh release edit failed. The release can be resumed from its draft."
    }
}

$isWindowsPlatform = if (Get-Variable -Name IsWindows -ErrorAction SilentlyContinue) {
    $IsWindows
} else {
    $env:OS -eq "Windows_NT"
}

if (-not $isWindowsPlatform) {
    throw "MSI packaging requires Windows because jpackage --type msi depends on WiX."
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptRoot

New-Item -ItemType Directory -Force -Path ".local-release\logs", ".local-release\msi", ".local-release\resources" | Out-Null
& (Join-Path $scriptRoot ".github\scripts\Validate-MsiLocalization.ps1")

if ($PSBoundParameters.ContainsKey("Version")) {
    Assert-ProjectVersion -Value $Version -Source "Version"
}

Ensure-Command "java" "Install JDK 25 and make sure java.exe is on PATH." "temurin25" "EclipseAdoptium.Temurin.25.JDK" $SkipDependencyInstall
Ensure-Command "jpackage" "Install JDK 25 and make sure jpackage.exe is on PATH." "temurin25" "EclipseAdoptium.Temurin.25.JDK" $SkipDependencyInstall
Ensure-Command "mvn" "Install Maven and make sure mvn.exe is on PATH." "maven" "Apache.Maven" $SkipDependencyInstall
Ensure-Wix -SkipInstall:$SkipDependencyInstall

$currentVersion = Get-ProjectVersion
Assert-ProjectVersion -Value $currentVersion -Source "Current pom.xml version"
Write-Host "Current project version: $currentVersion"

if ($Version -and -not $BuildOnly) {
    Set-ProjectVersion -NewVersion $Version.Trim()
} elseif ($Release -and (Prompt-YesNo "Bump pom.xml version before building?")) {
    $Version = Read-Host "New version"
    Set-ProjectVersion -NewVersion $Version.Trim()
}

$projectVersion = if ($BuildOnly -and $Version) { $Version.Trim() } else { Get-ProjectVersion }
Assert-ProjectVersion -Value $projectVersion -Source "Project version"
$buildLog = Join-Path ".local-release\logs" "msi-build-$projectVersion.log"
$iconPath = Join-Path $scriptRoot ".local-release\resources\ApocalipseWoW.ico"
New-WindowsIcon `
    -SourcePath "src\main\resources\Apocalipse_wow_icon.png" `
    -DestinationPath $iconPath
$resolvedIconPath = (Resolve-Path -LiteralPath $iconPath).Path
$signingCertificate = $null
$mavenOptions = @("--batch-mode", "--no-transfer-progress", "-Pjpackage-msi", "-Drevision=$projectVersion", "-Djpackage.icon=$resolvedIconPath")
if ($SigningCertificateThumbprint) {
    $SigningCertificateThumbprint = $SigningCertificateThumbprint -replace '\s', ''
    if ($SigningCertificateThumbprint -notmatch '^[0-9a-fA-F]{40}$') {
        throw "SigningCertificateThumbprint must be the Windows certificate store's 40-character thumbprint."
    }
    Get-Command signtool.exe -ErrorAction Stop | Out-Null
    $signingCertificate = Get-Item -LiteralPath "Cert:\CurrentUser\My\$SigningCertificateThumbprint"
    if (-not $signingCertificate.HasPrivateKey) {
        throw "The signing certificate needs an accessible private key."
    }
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $publisherSha256 = [BitConverter]::ToString($sha256.ComputeHash($signingCertificate.RawData)).Replace("-", "")
    } finally {
        $sha256.Dispose()
    }
    $mavenOptions += "-Dlauncher.updatePublisherSha256=$publisherSha256"
}

try {
    $buildStartedAt = Get-Date
    Invoke-LoggedCommand `
        -LogPath $buildLog `
        -FilePath "mvn" `
        -Arguments ($mavenOptions + @("clean", "verify"))
} catch {
    $buildLogText = Get-Content -LiteralPath $buildLog -Raw -ErrorAction SilentlyContinue
    if ($buildLogText -notlike "*The configured main jar does not exist*") {
        throw
    }

    Write-Host "jpackage did not see the copied main jar immediately after clean. Retrying without clean..."
    $buildStartedAt = Get-Date
    Invoke-LoggedCommand `
        -LogPath $buildLog `
        -FilePath "mvn" `
        -Arguments ($mavenOptions + @("verify"))
}

$msi = Get-ChildItem -Path "target\jpackage" -Filter "ApocalipseWoW-$projectVersion.msi" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -ge $buildStartedAt } |
    Sort-Object -Property LastWriteTime -Descending |
    Select-Object -First 1

if (-not $msi) {
    throw "No MSI was produced under target\jpackage. See $buildLog"
}

$releaseMsi = Join-Path ".local-release\msi" $msi.Name
Copy-Item -LiteralPath $msi.FullName -Destination $releaseMsi -Force
if ($signingCertificate) {
    & signtool.exe sign /sha1 $SigningCertificateThumbprint /fd SHA256 /tr $TimestampUrl /td SHA256 $releaseMsi
    if ($LASTEXITCODE -ne 0) {
        throw "SignTool could not sign the MSI."
    }
    $signature = Get-AuthenticodeSignature -LiteralPath $releaseMsi
    if ($signature.Status -ne "Valid" -or $signature.SignerCertificate.Thumbprint -ne $SigningCertificateThumbprint) {
        throw "Windows could not verify the MSI signature for the requested publisher."
    }
}
Write-Host "MSI copied to: $releaseMsi"
Write-Host "Build log: $buildLog"
Write-Host "The installer is configured to create a desktop shortcut at: $([Environment]::GetFolderPath('CommonDesktopDirectory'))\ApocalipseWoW.lnk"
Write-Host "A diagnostic console launcher is installed at: C:\Program Files\ApocalipseWoW\ApocalipseWoWDebug.exe"
Write-Host "If the shortcut opens nothing, inspect: $env:LOCALAPPDATA\ApocalipseWoWLauncher\launcher.log"

if ($BuildOnly) {
    return
}

if (-not $Release) {
    $Release = Prompt-YesNo "Create or update a GitHub release with this MSI?"
}

if ($Release) {
    $tagName = "launcher-v$projectVersion"
    Publish-Release -TagName $tagName -ProjectVersion $projectVersion -MsiPath $releaseMsi
    Write-Host "Release complete: $tagName"
}
