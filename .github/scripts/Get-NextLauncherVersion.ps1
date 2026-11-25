[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('patch', 'minor', 'major')]
    [string]$Bump,
    [Parameter(Mandatory)]
    [version]$Baseline,
    [string[]]$Tags = @()
)

function Test-MsiVersion([version]$Version) {
    return $Version.Major -le 255 -and $Version.Minor -le 255 -and
        $Version.Build -ge 0 -and $Version.Build -le 65535 -and $Version.Revision -eq -1
}

if (-not (Test-MsiVersion $Baseline)) {
    throw "The baseline version $Baseline does not fit the MSI version fields."
}

$latest = $Baseline
foreach ($tag in $Tags) {
    if ($tag -cmatch '^launcher-v(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,4})$') {
        $candidate = [version]$tag.Substring('launcher-v'.Length)
        if ((Test-MsiVersion $candidate) -and $candidate -gt $latest) {
            $latest = $candidate
        }
    }
}

$next = switch ($Bump) {
    'major' { [version]::new(($latest.Major + 1), 0, 0) }
    'minor' { [version]::new($latest.Major, ($latest.Minor + 1), 0) }
    'patch' { [version]::new($latest.Major, $latest.Minor, ($latest.Build + 1)) }
}
if (-not (Test-MsiVersion $next)) {
    throw "The $Bump bump from $latest exceeds the MSI version limits. Choose a different bump."
}
$next.ToString(3)
