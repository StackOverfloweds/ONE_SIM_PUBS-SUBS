#!/bin/bash
# Skrip untuk menjalankan simulator

# Proceed with the simulation
CONFIG_FILE=${1:-"./conf/PublishAndSubsc_Setting.txt"}
BATCH_MODE=${2:-1}  # Nilai default untuk batch mode adalah 1

# Periksa apakah file konfigurasi ada
if [ ! -f "$CONFIG_FILE" ]; then
  echo "Configuration file $CONFIG_FILE not found!"
  exit 1
fi

echo "Running The ONE Simulator with config: $CONFIG_FILE in batch mode: $BATCH_MODE"

# Gunakan direktori keluaran 
java -cp "out:lib/*" core.DTNSim -b "$BATCH_MODE" "$CONFIG_FILE"


echo "Simulation completed. Exiting."
