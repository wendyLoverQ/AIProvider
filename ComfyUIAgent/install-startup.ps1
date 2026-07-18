$ErrorActionPreference = 'Stop'
$exe = Join-Path $PSScriptRoot 'ComfyUIAgent.exe'
if (-not (Test-Path -LiteralPath $exe)) { throw "ComfyUIAgent.exe not found beside this script." }
$shortcut = Join-Path ([Environment]::GetFolderPath('Startup')) 'Local ComfyUI Bridge.lnk'
$shell = New-Object -ComObject WScript.Shell
$link = $shell.CreateShortcut($shortcut)
$link.TargetPath = $exe
$link.WorkingDirectory = $PSScriptRoot
$link.WindowStyle = 7
$link.Description = 'Local-only ComfyUI browser bridge'
$link.Save()

$protocolRoot = 'HKCU:\Software\Classes\aiprovider-bridge'
$commandKey = Join-Path $protocolRoot 'shell\open\command'
New-Item -Path $commandKey -Force | Out-Null
Set-Item -Path $protocolRoot -Value 'URL:AIProvider Local Bridge Protocol'
New-ItemProperty -Path $protocolRoot -Name 'URL Protocol' -Value '' -PropertyType String -Force | Out-Null
Set-Item -Path $commandKey -Value ('"' + $exe + '" "%1"')

Write-Host "Installed startup shortcut: $shortcut"
Write-Host 'Registered browser launch protocol: aiprovider-bridge://start'
