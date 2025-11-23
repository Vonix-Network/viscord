@echo off
REM ====================================================================
REM Viscord Universal Build Script
REM Builds all three versions: NeoForge 1.21.1, Forge 1.20.1, Forge 1.21.1
REM ====================================================================

echo.
echo ====================================================================
echo Viscord Universal Build Script
echo ====================================================================
echo.
echo This script will build all three Viscord versions:
echo   1. NeoForge 1.21.1
echo   2. Forge 1.20.1
echo   3. Forge 1.21.1
echo.
echo Output will be copied to: Universal-Build\
echo.
pause

REM Create Universal-Build directory if it doesn't exist
if not exist "Universal-Build" (
    echo Creating Universal-Build directory...
    mkdir Universal-Build
)

REM Clean previous builds from Universal-Build
echo.
echo Cleaning previous builds...
del /Q Universal-Build\*.jar 2>nul

REM ====================================================================
REM Build NeoForge 1.21.1
REM ====================================================================
echo.
echo ====================================================================
echo [1/3] Building NeoForge 1.21.1...
echo ====================================================================
echo.

cd neoforge-1.21.1
call gradlew.bat clean build
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: NeoForge 1.21.1 build failed!
    cd ..
    pause
    exit /b 1
)
cd ..

echo.
echo Copying NeoForge 1.21.1 JAR...
copy "neoforge-1.21.1\build\libs\viscord-1.0.2.jar" "Universal-Build\viscord-1.0.2-neoforge-1.21.1.jar"
echo NeoForge 1.21.1 build complete!

REM ====================================================================
REM Build Forge 1.20.1
REM ====================================================================
echo.
echo ====================================================================
echo [2/3] Building Forge 1.20.1...
echo ====================================================================
echo.

cd forge-1.20.1-47.4.0-mdk
call gradlew.bat clean build
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Forge 1.20.1 build failed!
    cd ..
    pause
    exit /b 1
)
cd ..

echo.
echo Copying Forge 1.20.1 JAR...
copy "forge-1.20.1-47.4.0-mdk\build\libs\viscord-1.0.2.jar" "Universal-Build\viscord-1.0.2-forge-1.20.1.jar"
echo ✓ Forge 1.20.1 build complete!

REM ====================================================================
REM Build Forge 1.21.1
REM ====================================================================
echo.
echo ====================================================================
echo [3/3] Building Forge 1.21.1...
echo ====================================================================
echo.

cd forge-1.21.1-52.1.0-mdk
call gradlew.bat clean build
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Forge 1.21.1 build failed!
    cd ..
    pause
    exit /b 1
)
cd ..

echo.
echo Copying Forge 1.21.1 JAR...
copy "forge-1.21.1-52.1.0-mdk\build\libs\viscord-1.0.2.jar" "Universal-Build\viscord-1.0.2-forge-1.21.1.jar"
echo ✓ Forge 1.21.1 build complete!

REM ====================================================================
REM Summary
REM ====================================================================
echo.
echo ====================================================================
echo BUILD SUMMARY
echo ====================================================================
echo.
echo All versions built successfully!
echo.
echo Output files in Universal-Build\:
echo.
dir /B Universal-Build\*.jar
echo.
echo File sizes:
dir Universal-Build\*.jar | find ".jar"
echo.
echo ====================================================================
echo.
echo Build complete! All JAR files are in the Universal-Build folder.
echo.
pause
