#!/bin/bash
# ====================================================================
# Viscord Universal Build Script (Unix/Linux/macOS)
# Builds all three versions: NeoForge 1.21.1, Forge 1.20.1, Forge 1.21.1
# ====================================================================

echo ""
echo "===================================================================="
echo "Viscord Universal Build Script"
echo "===================================================================="
echo ""
echo "This script will build all three Viscord versions:"
echo "  1. NeoForge 1.21.1"
echo "  2. Forge 1.20.1"
echo "  3. Forge 1.21.1"
echo ""
echo "Output will be copied to: Universal-Build/"
echo ""
read -p "Press Enter to continue..."

# Create Universal-Build directory if it doesn't exist
if [ ! -d "Universal-Build" ]; then
    echo "Creating Universal-Build directory..."
    mkdir -p Universal-Build
fi

# Clean previous builds from Universal-Build
echo ""
echo "Cleaning previous builds..."
rm -f Universal-Build/*.jar

# ====================================================================
# Build NeoForge 1.21.1
# ====================================================================
echo ""
echo "===================================================================="
echo "[1/3] Building NeoForge 1.21.1..."
echo "===================================================================="
echo ""

cd viscord-template-1.21.1
./gradlew clean build
if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: NeoForge 1.21.1 build failed!"
    cd ..
    exit 1
fi
cd ..

echo ""
echo "Copying NeoForge 1.21.1 JAR..."
cp "viscord-template-1.21.1/build/libs/viscord-1.0.0.jar" "Universal-Build/viscord-1.0.0-neoforge-1.21.1.jar"
echo "✓ NeoForge 1.21.1 build complete!"

# ====================================================================
# Build Forge 1.20.1
# ====================================================================
echo ""
echo "===================================================================="
echo "[2/3] Building Forge 1.20.1..."
echo "===================================================================="
echo ""

cd forge-1.20.1-47.4.0-mdk
./gradlew clean build
if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Forge 1.20.1 build failed!"
    cd ..
    exit 1
fi
cd ..

echo ""
echo "Copying Forge 1.20.1 JAR..."
cp "forge-1.20.1-47.4.0-mdk/build/libs/viscord-1.0.0.jar" "Universal-Build/viscord-1.0.0-forge-1.20.1.jar"
echo "✓ Forge 1.20.1 build complete!"

# ====================================================================
# Build Forge 1.21.1
# ====================================================================
echo ""
echo "===================================================================="
echo "[3/3] Building Forge 1.21.1..."
echo "===================================================================="
echo ""

cd forge-1.21.1-52.1.0-mdk
./gradlew clean build
if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Forge 1.21.1 build failed!"
    cd ..
    exit 1
fi
cd ..

echo ""
echo "Copying Forge 1.21.1 JAR..."
cp "forge-1.21.1-52.1.0-mdk/build/libs/viscord-1.0.0.jar" "Universal-Build/viscord-1.0.0-forge-1.21.1.jar"
echo "✓ Forge 1.21.1 build complete!"

# ====================================================================
# Summary
# ====================================================================
echo ""
echo "===================================================================="
echo "BUILD SUMMARY"
echo "===================================================================="
echo ""
echo "All versions built successfully!"
echo ""
echo "Output files in Universal-Build/:"
echo ""
ls -lh Universal-Build/*.jar
echo ""
echo "===================================================================="
echo ""
echo "Build complete! All JAR files are in the Universal-Build folder."
echo ""
