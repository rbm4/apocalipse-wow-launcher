$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$env:PSModulePath = [IO.Path]::Combine($PSHOME, 'Modules')
$ready = $false
$lock = $null
$package = $null

function Show-UpdateMessage($message) {
    Add-Type -AssemblyName System.Windows.Forms
    [void][Windows.Forms.MessageBox]::Show($message, 'Apocalipse BR Launcher')
}

try {
    $parent = [Diagnostics.Process]::GetProcessById([int]$env:APOC_UPDATE_PID)
    $msi = $env:APOC_UPDATE_MSI
    $log = Join-Path (Get-Location).Path 'installer.log'
    $lockPath = Join-Path (Split-Path -Parent $msi) 'install.lock'
    $lock = [IO.File]::Open($lockPath, 'OpenOrCreate', 'ReadWrite', 'None')
    $package = [IO.File]::Open($msi, 'Open', 'Read', 'Read')

    $digest = (Get-FileHash -InputStream $package -Algorithm SHA256).Hash
    if ($digest -ne $env:APOC_UPDATE_HASH) {
        throw 'Launcher MSI SHA-256 changed after download.'
    }
    $signature = Get-AuthenticodeSignature -LiteralPath $msi
    if ($signature.Status -ne 'Valid') {
        throw "Windows rejected the MSI signature: $($signature.Status)."
    }
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $publisher = [BitConverter]::ToString($sha256.ComputeHash($signature.SignerCertificate.RawData)).Replace('-', '')
    } finally {
        $sha256.Dispose()
    }
    if ($publisher -ne $env:APOC_UPDATE_SIGNER) {
        throw 'The MSI signing certificate does not match the configured publisher.'
    }

    $installer = New-Object -ComObject WindowsInstaller.Installer
    $database = $installer.OpenDatabase($msi, 0)
    $view = $database.OpenView('SELECT * FROM Property')
    $properties = @{}
    try {
        $view.Execute()
        while ($null -ne ($row = $view.Fetch())) {
            $properties[$row.StringData(1)] = $row.StringData(2)
            [void][Runtime.InteropServices.Marshal]::ReleaseComObject($row)
        }
    } finally {
        $view.Close()
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($view)
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($database)
    }
    if ($properties.ProductVersion -ne $env:APOC_UPDATE_VERSION -or
            $properties.UpgradeCode -ne '{FE8A577B-C3F4-34D6-9126-F9F345F6CC45}' -or
            $properties.ALLUSERS -ne '1') {
        throw 'MSI version, upgrade family or installation scope does not match the launcher.'
    }

    [IO.File]::WriteAllText((Join-Path (Get-Location).Path 'ready'), '')
    $ready = $true
    if (-not $parent.WaitForExit(30000)) {
        throw 'The launcher did not close in time.'
    }
    $processNames = @(
        [IO.Path]::GetFileNameWithoutExtension($env:APOC_UPDATE_EXE),
        "$([IO.Path]::GetFileNameWithoutExtension($env:APOC_UPDATE_EXE))Debug",
        $properties.ProductName,
        "$($properties.ProductName)Debug"
    ) | Select-Object -Unique
    $otherLauncher = Get-Process -ErrorAction SilentlyContinue | Where-Object {
        $processNames -icontains $_.ProcessName
    }
    if ($otherLauncher) {
        throw 'Another launcher instance is still open.'
    }
    $msiexec = Join-Path ([Environment]::SystemDirectory) 'msiexec.exe'
    $arguments = '/i "{0}" /passive /norestart /L*v "{1}"' -f $msi, $log
    $result = Start-Process -FilePath $msiexec -ArgumentList $arguments -Verb RunAs -Wait -PassThru
    Write-Output "Windows Installer exit code: $($result.ExitCode)"
    if ($result.ExitCode -in 3010, 1641) {
        Show-UpdateMessage $env:APOC_UPDATE_REBOOT
        exit 0
    }
    if ($result.ExitCode -ne 0) {
        throw "Windows Installer failed with exit code $($result.ExitCode)."
    }
    $package.Dispose()
    $package = $null
    Remove-Item -LiteralPath $msi -ErrorAction SilentlyContinue
    $installDirectory = Join-Path ([Environment]::GetFolderPath('ProgramFiles')) $properties.ProductName
    $installedExecutable = Join-Path $installDirectory ($properties.ProductName + '.exe')
    if (-not (Test-Path -LiteralPath $installedExecutable -PathType Leaf)) {
        throw "Updated launcher executable was not found: $installedExecutable"
    }
    Start-Process -FilePath $installedExecutable -WorkingDirectory $installDirectory
} catch {
    Write-Output $_.Exception.Message
    if ($ready) {
        Show-UpdateMessage ($env:APOC_UPDATE_FAILURE + [Environment]::NewLine + (Get-Location).Path)
    }
    exit 1
} finally {
    if ($null -ne $package) {
        $package.Dispose()
    }
    if ($null -ne $lock) {
        $lock.Dispose()
    }
}
