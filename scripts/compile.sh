#!/bin/bash
# Skrip untuk mengompilasi The ONE Simulator

echo "Starting compilation for The ONE Simulator..."

# Direktori keluaran utama
OUT_DIR="out"
TARGET_DIR="target"

# Daftar dependensi/library
LIBS="lib/ECLA.jar:lib/DTNConsoleConnection.jar:lib/jFuzzyLogic.jar:lib/uncommons-maths-1.2.1.jar:lib/lombok.jar:lib/fastjson-1.2.7.jar"

# File sumber yang akan dikompilasi
SOURCE_PATHS=(
    "src/core/*.java"
    "src/reinforcement/actionselection/*.java"
    "src/reinforcement/models/*.java"
    "src/reinforcement/qlearn/*.java"
    "src/reinforcement/utils/*.java"
    "src/movement/*.java"
    "src/report/*.java"
    "src/routing/*.java"
    "src/gui/*.java"
    "src/input/*.java"
    "src/applications/*.java"
    "src/interfaces/*.java"
    "java"
    "src/routing/peopleRankActive.java"
)

# Pastikan direktori keluaran utama ada
mkdir -p "$OUT_DIR"

# Pastikan direktori target ada
mkdir -p "$TARGET_DIR"

# Kompilasi file sumber ke direktori keluaran utama
echo "Compiling to $OUT_DIR..."
javac -d "$OUT_DIR" -sourcepath src -cp "lib/*" $(find src -name "*.java")

# Kompilasi file sumber ke direktori target dengan library spesifik
echo "Compiling with specific dependencies to $TARGET_DIR..."
javac -sourcepath src -d "$TARGET_DIR" -cp "$LIBS" "${SOURCE_PATHS[@]}"

# Salin aset grafik tombol ke direktori target jika belum ada
if [ ! -d "$TARGET_DIR/gui/buttonGraphics" ]; then
    echo "Copying button graphics to $TARGET_DIR/gui/buttonGraphics..."
    mkdir -p "$TARGET_DIR/gui/buttonGraphics"
    cp src/gui/buttonGraphics/* "$TARGET_DIR/gui/buttonGraphics/"
fi

echo "Compilation finished successfully!"
