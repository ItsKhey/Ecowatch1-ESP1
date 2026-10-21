# Shows the API base URL to use for this machine, and verifies the server answers.
#
# Run from the Android project root:
#
#   powershell -ExecutionPolicy Bypass -File .\tools\print-api-url.ps1
#
# Picks the adapter a phone can actually reach:
#   1. a hotspot adapter (192.168.137.x) when the phone is on your hotspot
#   2. otherwise the default-route adapter, which is your Wi-Fi or Ethernet
#
# This matters because a hotspot gives the machine a second address, and using
# the wrong one produces an app that silently cannot reach the server.
param(
    [switch]$Wait
)

$ErrorActionPreference = 'Continue'

function Get-UsableIPv4 {
    $adapters = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object {
            $_.IPAddress -notlike '127.*' -and
            $_.IPAddress -notlike '169.254.*' -and
            (Get-NetAdapter -Name $_.InterfaceAlias -ErrorAction SilentlyContinue).Status -eq 'Up'
        }

    if (-not $adapters) { return $null }

    # A hotspot adapter is the one a tethered phone shares a network with, and it
    # is the whole reason this script prefers it over the default route.
    $hotspot = $adapters | Where-Object { $_.InterfaceAlias -match 'Local Area Connection\*|Mobile Hotspot|ICS|Wi-Fi Direct' } |
        Sort-Object IPAddress
    if ($hotspot) { return $hotspot[0] }

    # Otherwise follow the default route, which is the interface the machine
    # actually uses to reach the internet.
    $default = Get-NetRoute -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue |
        Sort-Object RouteMetric
    foreach ($route in $default) {
        $match = $adapters | Where-Object { $_.InterfaceAlias -eq $route.InterfaceAlias } | Select-Object -First 1
        if ($match) { return $match }
    }

    return $adapters[0]
}

$port = 5000
if (Test-Path .env) {
    $line = Get-Content .env -ErrorAction SilentlyContinue | Where-Object { $_ -match '^\s*PORT=' } | Select-Object -First 1
    if ($line) { $port = ($line -split '=')[1].Trim() }
}

$chosen = Get-UsableIPv4
if (-not $chosen) {
    Write-Host 'No usable IPv4 address found. Is Wi-Fi or the hotspot on?' -ForegroundColor Red
    exit 1
}

$url = "http://$($chosen.IPAddress):$port/api/"

Write-Host ''
Write-Host '  EcoWatch API base URL for a real phone' -ForegroundColor Cyan
Write-Host '  -----------------------------------------' -ForegroundColor Cyan
Write-Host "  adapter : $($chosen.InterfaceAlias)"
Write-Host "  address : $($chosen.IPAddress)"
Write-Host "  url     : $url"
Write-Host ''
Write-Host '  Build the app with:' -ForegroundColor Cyan
Write-Host "    .\gradlew.bat installDebug -PecowatchApiBaseUrl=$url" -ForegroundColor White
Write-Host ''

if ($Wait) {
    Write-Host '  Waiting for the server to answer...' -ForegroundColor DarkGray
    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $deadline) {
        try {
            $health = Invoke-RestMethod -Uri "http://$($chosen.IPAddress):$port/api/health" -TimeoutSec 5
            Write-Host '  Server responded:' -ForegroundColor Green
            Write-Host "    status          : $($health.status)"
            Write-Host "    database target : $($health.databaseTarget)"
            Write-Host "    image storage   : $($health.imageStorage)"
            Write-Host ''
            Write-Host '  Sanity check from the phone browser:' -ForegroundColor Cyan
            Write-Host "    http://$($chosen.IPAddress):$port/api/health" -ForegroundColor White
            exit 0
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    Write-Host '  No response yet. Is the server running? Start it with: npm run dev' -ForegroundColor Yellow
}
