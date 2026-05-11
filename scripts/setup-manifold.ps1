<#
.SYNOPSIS
  Sets up Manifold + jextract for the pet-CAD project.

.DESCRIPTION
  - Clones and builds manifoldc via CMake (vcpkg pulls deps: glm, clipper2, tbb).
  - Copies manifoldc.dll (+ tbb*.dll) into src/main/resources/native/windows-x86_64/.
  - Copies manifoldc.h into native/include/.
  - Downloads jextract into tools/jextract/ (unless -SkipJextract).
  - Runs gradlew jextract.

.PARAMETER ManifoldTag
  Git tag/branch for elalish/manifold. Recommend a fixed tag (e.g. v3.0.1).

.PARAMETER VcpkgRoot
  Path to vcpkg. Falls back to $env:VCPKG_ROOT, then tools/vcpkg.

.PARAMETER JextractUrl
  URL of jextract Windows x64 tar.gz. If 404, get a fresh link from
  https://jdk.java.net/jextract/ and pass via -JextractUrl.

.PARAMETER Force
  Rebuild manifoldc even if the DLL is already present.

.PARAMETER SkipJextract
  Do not download jextract.

.EXAMPLE
  .\scripts\setup-manifold.ps1 -ManifoldTag v3.0.1
#>

[CmdletBinding()]
param(
    [string]$ManifoldTag = "main",
    [string]$VcpkgRoot = "",
    [string]$JextractUrl = "https://download.java.net/java/early_access/jextract/22/5/openjdk-22-jextract+5-33_windows-x64_bin.tar.gz",
    [switch]$Force,
    [switch]$SkipJextract
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$ToolsDir    = Join-Path $ProjectRoot "tools"
$NativeDir   = Join-Path $ProjectRoot "src\main\resources\native\windows-x86_64"
$HeaderDir   = Join-Path $ProjectRoot "native\include"

function Section($msg) {
    Write-Host ""
    Write-Host "==> $msg" -ForegroundColor Cyan
}

function Require-Tool($name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Required tool not in PATH: $name"
    }
}

# --- 1. Tool checks --------------------------------------------------------

Section "Checking required tools"
Require-Tool git
Require-Tool cmake
Write-Host "git, cmake: OK"

if (-not (Get-Command cl.exe -ErrorAction SilentlyContinue)) {
    Write-Warning "cl.exe (MSVC) not in PATH. Launch this script from 'x64 Native Tools Command Prompt for VS', or install Visual Studio Build Tools (Desktop development with C++)."
    Write-Warning "CMake will try to find a compiler; if it fails, re-run from the VS console."
}

New-Item -ItemType Directory -Force -Path $ToolsDir, $NativeDir, $HeaderDir | Out-Null

# --- 2. vcpkg --------------------------------------------------------------

Section "Locating vcpkg"
if ($VcpkgRoot -eq "" -and $env:VCPKG_ROOT) {
    $VcpkgRoot = $env:VCPKG_ROOT
}
if ($VcpkgRoot -eq "") {
    $VcpkgRoot = Join-Path $ToolsDir "vcpkg"
}

if (-not (Test-Path (Join-Path $VcpkgRoot ".git"))) {
    Write-Host "Cloning vcpkg to $VcpkgRoot ..."
    git clone --depth 1 https://github.com/microsoft/vcpkg.git $VcpkgRoot
}

$vcpkgExe = Join-Path $VcpkgRoot "vcpkg.exe"
if (-not (Test-Path $vcpkgExe)) {
    Write-Host "Bootstrapping vcpkg..."
    & (Join-Path $VcpkgRoot "bootstrap-vcpkg.bat") -disableMetrics
}
Write-Host "vcpkg: $vcpkgExe"

$VcpkgToolchain = Join-Path $VcpkgRoot "scripts\buildsystems\vcpkg.cmake"
$VcpkgTriplet = "x64-windows"

Section "Installing vcpkg dependencies (clipper2, glm, tbb)"
# Manifold uses FetchContent for these by default; we force it to use system
# packages provided by vcpkg below via -DMANIFOLD_DOWNLOADS=OFF.
& $vcpkgExe install "clipper2:$VcpkgTriplet" "glm:$VcpkgTriplet" "tbb:$VcpkgTriplet"
if ($LASTEXITCODE -ne 0) { throw "vcpkg install failed" }

# --- 3. Manifold: clone + build --------------------------------------------

Section "Building manifoldc"
$ManifoldDir = Join-Path $ToolsDir "manifold"
$ManifoldBuildDir = Join-Path $ManifoldDir "build"
$ManifoldDll = Join-Path $ManifoldBuildDir "bin\Release\manifoldc.dll"

$skipBuild = (Test-Path $ManifoldDll) -and (-not $Force)
if ($skipBuild) {
    Write-Host "manifoldc.dll already exists, skipping build (use -Force to rebuild)"
} else {
    if (-not (Test-Path (Join-Path $ManifoldDir ".git"))) {
        Write-Host "Cloning manifold ($ManifoldTag) to $ManifoldDir ..."
        git clone https://github.com/elalish/manifold.git $ManifoldDir
    }
    Push-Location $ManifoldDir
    try {
        git fetch --tags
        git checkout $ManifoldTag
        git submodule update --init --recursive
    } finally {
        Pop-Location
    }

    $VcpkgInstalled = Join-Path $VcpkgRoot "installed\$VcpkgTriplet"
    Write-Host "Configuring CMake (vcpkg installed dir: $VcpkgInstalled)..."
    # VCPKG_MANIFEST_MODE=OFF forces classic-mode lookup so manifold finds
    # clipper2/glm/tbb that we just installed globally with vcpkg install.
    # Otherwise vcpkg may try to do per-project manifest installs and miss them.
    cmake -B $ManifoldBuildDir -S $ManifoldDir `
        -DMANIFOLD_C_API=ON `
        -DMANIFOLD_DOWNLOADS=OFF `
        -DMANIFOLD_TEST=OFF `
        -DMANIFOLD_EXPORT=OFF `
        -DCMAKE_BUILD_TYPE=Release `
        -DCMAKE_TOOLCHAIN_FILE="$VcpkgToolchain" `
        -DVCPKG_TARGET_TRIPLET=$VcpkgTriplet `
        -DVCPKG_MANIFEST_MODE=OFF `
        -DCMAKE_PREFIX_PATH="$VcpkgInstalled"
    if ($LASTEXITCODE -ne 0) { throw "CMake configure failed" }

    Write-Host "Building..."
    cmake --build $ManifoldBuildDir --config Release --target manifoldc
    if ($LASTEXITCODE -ne 0) { throw "CMake build failed" }
}

if (-not (Test-Path $ManifoldDll)) {
    $alt = Get-ChildItem -Path $ManifoldBuildDir -Recurse -Filter manifoldc.dll -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($alt) { $ManifoldDll = $alt.FullName }
}
if (-not (Test-Path $ManifoldDll)) {
    throw "manifoldc.dll not found after build. Check $ManifoldBuildDir manually."
}

# --- 4. Copy artifacts -----------------------------------------------------

Section "Copying artifacts into project"

Copy-Item $ManifoldDll (Join-Path $NativeDir "manifoldc.dll") -Force
Write-Host "DLL -> $NativeDir\manifoldc.dll"

# manifoldc.dll is a thin C wrapper; the heavy lifting is in manifold.dll.
$manifoldMain = Get-ChildItem -Path $ManifoldBuildDir -Recurse -Filter manifold.dll -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch 'manifoldc' } |
    Select-Object -First 1
if ($manifoldMain) {
    Copy-Item $manifoldMain.FullName (Join-Path $NativeDir $manifoldMain.Name) -Force
    Write-Host "DLL -> $NativeDir\$($manifoldMain.Name)"
}

# tbb runtime: manifoldc links against TBB. With vcpkg the DLLs live in
# installed/x64-windows/bin, not next to manifoldc.dll. Search both locations.
$tbbCandidates = @(
    Split-Path $ManifoldDll -Parent
    Join-Path $VcpkgRoot "installed\$VcpkgTriplet\bin"
)
foreach ($dir in $tbbCandidates) {
    if (Test-Path $dir) {
        Get-ChildItem -Path $dir -Filter "tbb*.dll" -ErrorAction SilentlyContinue | ForEach-Object {
            Copy-Item $_.FullName (Join-Path $NativeDir $_.Name) -Force
            Write-Host "DLL -> $NativeDir\$($_.Name)"
        }
    }
}

$headerRoot = Join-Path $ManifoldDir "bindings\c\include"
$headerMain = Join-Path $headerRoot "manifold\manifoldc.h"
if (-not (Test-Path $headerMain)) {
    $alt = Get-ChildItem -Path $ManifoldDir -Recurse -Filter manifoldc.h -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($alt) {
        $headerMain = $alt.FullName
        # bindings/c/include is the directory containing the `manifold/` subdir.
        $headerRoot = Split-Path (Split-Path $headerMain -Parent) -Parent
    }
}
if (-not (Test-Path $headerMain)) {
    throw "manifoldc.h not found in cloned manifold tree"
}

# Copy the entire 'manifold/' include subtree so #include "manifold/types.h" resolves.
$manifoldIncludeSrc = Join-Path $headerRoot "manifold"
$manifoldIncludeDst = Join-Path $HeaderDir "manifold"
if (Test-Path $manifoldIncludeDst) { Remove-Item -Recurse -Force $manifoldIncludeDst }
Copy-Item -Recurse $manifoldIncludeSrc $manifoldIncludeDst
Write-Host "Headers -> $manifoldIncludeDst\ (full subtree)"

# Patch K&R-style zero-arg declarations: 'foo()' -> 'foo(void)'.
# Otherwise jextract treats them as variadic and emits clunky invoker classes
# instead of plain static methods.
Get-ChildItem -Path $manifoldIncludeDst -Filter *.h -Recurse | ForEach-Object {
    $text = Get-Content $_.FullName -Raw
    # Match identifier followed by () with optional whitespace, but only on lines
    # that look like declarations (contain semicolon further on the same line).
    $patched = [regex]::Replace(
        $text,
        '(\b(?:manifold_|MANIFOLD_)\w+)\s*\(\s*\)(\s*;)',
        '$1(void)$2'
    )
    if ($patched -ne $text) {
        Set-Content -NoNewline -Encoding ASCII -Path $_.FullName -Value $patched
        Write-Host "  patched $($_.Name): K&R '()' -> '(void)'"
    }
}

# --- 5. jextract -----------------------------------------------------------

if (-not $SkipJextract) {
    Section "Installing jextract"
    $JextractDir = Join-Path $ToolsDir "jextract"
    $JextractBin = Join-Path $JextractDir "bin\jextract.bat"

    if (Test-Path $JextractBin) {
        Write-Host "jextract already at $JextractBin"
    } else {
        $archive = Join-Path $ToolsDir "jextract.tar.gz"
        Write-Host "Downloading jextract from $JextractUrl ..."
        Invoke-WebRequest -Uri $JextractUrl -OutFile $archive

        Write-Host "Extracting..."
        New-Item -ItemType Directory -Force -Path $JextractDir | Out-Null
        tar -xf $archive -C $JextractDir --strip-components=1
        if ($LASTEXITCODE -ne 0) { throw "tar extraction failed" }
        Remove-Item $archive

        if (-not (Test-Path $JextractBin)) {
            throw "jextract.bat not found at $JextractBin after extraction. Check archive layout."
        }
    }

    $env:JEXTRACT_HOME = $JextractDir
    Write-Host "JEXTRACT_HOME = $env:JEXTRACT_HOME"
    Write-Host "(add this to System Environment Variables to make it permanent)"
}

# --- 6. Run gradlew jextract -----------------------------------------------

Section "Running gradlew jextract"
Push-Location $ProjectRoot
try {
    & .\gradlew.bat jextract --no-daemon
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "gradlew jextract returned non-zero. Check output above; jextract may have skipped some symbols."
    }
} finally {
    Pop-Location
}

Section "Done"
Write-Host "Artifacts:" -ForegroundColor Green
Write-Host "  $NativeDir\manifoldc.dll"
Write-Host "  $HeaderDir\manifoldc.h"
Write-Host "  build\generated\sources\jextract\java\cad\native_\manifold\Manifoldc.java (if jextract succeeded)"
Write-Host ""
Write-Host "Next: implement ManifoldKernel.extrude/boolean on top of the generated bindings."
