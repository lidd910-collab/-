# ============================================================
# 不用装 Android Studio 的本地编译脚本（Windows / PowerShell 7）
# 前置：只需要装 JDK 17（约 180MB），Gradle 由本脚本自动下载
# 用法：在工程根目录执行  pwwsh -File build.ps1
# 产物：app\build\outputs\apk\debug\app-debug.apk
# ============================================================

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

# ---------- 1. 检查 JDK ----------
$javaOk = $false
try {
    $out = & java -version 2>&1
    if ($LASTEXITCODE -eq 0) { $javaOk = $true; Write-Host "[OK] 已检测到 JDK：$($out[0])" }
} catch { }

if (-not $javaOk) {
    Write-Host ""
    Write-Host "[X] 没有检测到 java。" -ForegroundColor Red
    Write-Host "    请先安装 JDK 17（Adoptium Temurin）后重开终端："
    Write-Host "    https://adoptium.net/temurin/releases/?version=17&os=windows&arch=x64&package=jdk"
    Write-Host "    安装时勾选 'Set JAVA_HOME variable' / 'Add to PATH'。"
    exit 1
}

# ---------- 2. 准备 Gradle 8.4 ----------
$tools = Join-Path $root '.tools'
$gradleHome = Join-Path $tools 'gradle-8.4'
$gradleBat = Join-Path $gradleHome 'bin\gradle.bat'

if (-not (Test-Path $gradleBat)) {
    New-Item -ItemType Directory -Force -Path $tools | Out-Null
    $zip = Join-Path $tools 'gradle-8.4-bin.zip'
    $urls = @(
        'https://mirrors.cloud.tencent.com/gradle/gradle-8.4-bin.zip',
        'https://services.gradle.org/distributions/gradle-8.4-bin.zip'
    )
    $done = $false
    foreach ($u in $urls) {
        try {
            Write-Host "[..] 下载 Gradle 8.4：$u"
            Invoke-WebRequest -Uri $u -OutFile $zip -TimeoutSec 300
            $done = $true
            break
        } catch {
            Write-Host "     该地址失败，换下一个"
        }
    }
    if (-not $done) { Write-Host "[X] Gradle 下载失败，请检查网络或手动下载到 .tools 目录" -ForegroundColor Red; exit 1 }
    Expand-Archive -Path $zip -DestinationPath $tools -Force
    Remove-Item $zip -Force
}

# ---------- 3. 编译 ----------
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
Write-Host "[..] 开始编译（首次会下载 Android SDK 依赖，约 5-15 分钟，取决于网速）"
& $gradleBat :app:assembleDebug --no-daemon --console=plain -p $root
if ($LASTEXITCODE -ne 0) {
    Write-Host "[X] 编译失败，把上面的报错整段贴出来即可定位" -ForegroundColor Red
    exit $LASTEXITCODE
}

# ---------- 4. 收尾 ----------
$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
if (Test-Path $apk) {
    Write-Host ""
    Write-Host "[OK] 编译成功：$apk" -ForegroundColor Green
    Write-Host "     装到手机：adb install -r `"$apk`""
    Write-Host "     或把 apk 复制到手机点击安装"
} else {
    Write-Host "[?] 没找到 apk，请检查上面的输出" -ForegroundColor Yellow
    exit 1
}
