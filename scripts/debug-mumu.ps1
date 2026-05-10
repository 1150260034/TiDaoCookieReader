param(
    [ValidateSet(
        "info",
        "launch-player",
        "build",
        "install",
        "run",
        "build-install-run",
        "logcat",
        "status",
        "read-cookie",
        "copy-all",
        "check-welfare",
        "upload-cookie",
        "home",
        "back"
    )]
    [string]$Action = "info",

    [string]$VmIndex = "0",
    [string]$Package = "com.tidao.wuxia.app",
    [string]$MuMuManager = "C:\Program Files\Netease\MuMu\nx_main\MuMuManager.exe"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$Gradle = Join-Path $ProjectRoot "gradlew.bat"
$Apk = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"

function Invoke-MuMu {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    if (!(Test-Path $MuMuManager)) {
        throw "MuMuManager not found: $MuMuManager"
    }
    & $MuMuManager @Args
}

function Invoke-GradleBuild {
    if (!(Test-Path $Gradle)) {
        throw "Gradle wrapper not found: $Gradle"
    }
    Push-Location $ProjectRoot
    try {
        & $Gradle assembleDebug
    } finally {
        Pop-Location
    }
}

function Install-App {
    if (!(Test-Path $Apk)) {
        throw "APK not found: $Apk. Run: scripts\debug-mumu.ps1 build"
    }
    Invoke-MuMu control --vmindex $VmIndex app install --apk $Apk
}

function Launch-App {
    Invoke-MuMu control --vmindex $VmIndex launch --package $Package
}

function Send-AutomationBroadcast {
    param([string]$Name)
    $actionName = "com.tidao.wuxia.app.action.$Name"
    Invoke-MuMu adb --vmindex $VmIndex --cmd "shell am broadcast -a $actionName -p $Package"
}

switch ($Action) {
    "info" {
        Invoke-MuMu info --vmindex $VmIndex
    }
    "launch-player" {
        Invoke-MuMu control --vmindex $VmIndex launch
    }
    "build" {
        Invoke-GradleBuild
    }
    "install" {
        Install-App
    }
    "run" {
        Launch-App
    }
    "build-install-run" {
        Invoke-GradleBuild
        Install-App
        Launch-App
    }
    "logcat" {
        Invoke-MuMu adb --vmindex $VmIndex --cmd "logcat -v time TidaoResult:I WebViewCookieReader:D AndroidRuntime:E System.err:W *:S"
    }
    "status" {
        Send-AutomationBroadcast "GET_STATUS"
    }
    "read-cookie" {
        Send-AutomationBroadcast "READ_COOKIE"
    }
    "copy-all" {
        Send-AutomationBroadcast "COPY_ALL"
    }
    "check-welfare" {
        Send-AutomationBroadcast "CHECK_WELFARE"
    }
    "upload-cookie" {
        Send-AutomationBroadcast "UPLOAD_COOKIE"
    }
    "home" {
        Invoke-MuMu sh --vmindex $VmIndex --cmd "go_home"
    }
    "back" {
        Invoke-MuMu sh --vmindex $VmIndex --cmd "go_back"
    }
}
