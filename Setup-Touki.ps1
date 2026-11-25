param(
    [string]$ZipPath = ".\ToukiShortDrama-Android-Source.zip",
    [string]$ProjectPath = ".\ToukiShortDrama"
)
$ErrorActionPreference = "Stop"
if (!(Test-Path $ZipPath)) { throw "ZIP not found: $ZipPath. Download ToukiShortDrama-Android-Source.zip first." }
if (Test-Path $ProjectPath) { throw "Folder already exists: $ProjectPath. Rename/delete it or choose another -ProjectPath." }
Expand-Archive -Path $ZipPath -DestinationPath $ProjectPath
Set-Location $ProjectPath
git init
git add .
git commit -m "Initial Touki Short Drama Android app"
Write-Host ""
Write-Host "Touki project created at: $((Get-Location).Path)" -ForegroundColor Green
Write-Host "Next: create an empty GitHub repository, then run:"
Write-Host 'git branch -M main'
Write-Host 'git remote add origin https://github.com/YOUR-USERNAME/YOUR-REPOSITORY.git'
Write-Host 'git push -u origin main'
Write-Host ""
Write-Host "Open this folder in Android Studio to build the APK."
