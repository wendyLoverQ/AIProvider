$shortcut = Join-Path ([Environment]::GetFolderPath('Startup')) 'Local ComfyUI Bridge.lnk'
Remove-Item -LiteralPath $shortcut -Force -ErrorAction SilentlyContinue
$protocolRoot = 'HKCU:\Software\Classes\aiprovider-bridge'
if (Test-Path -LiteralPath $protocolRoot) {
    Remove-Item -LiteralPath $protocolRoot -Recurse -Force
}
Write-Host 'Local ComfyUI Bridge startup shortcut removed.'
Write-Host 'Local ComfyUI Bridge browser launch protocol removed.'
