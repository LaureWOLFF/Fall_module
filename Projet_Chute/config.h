#pragma once
#include <Arduino.h>

// ===== Matériel (MPU6050 en I2C) =====
constexpr int     SDA_PIN  = 6;
constexpr int     SCL_PIN  = 7;
constexpr uint8_t MPU_ADDR = 0x68;

// ===== Signal (doit correspondre à l'entraînement du modèle) =====
constexpr int   FS_HZ          = 50;     // 50 échantillons par seconde
constexpr int   WINDOW         = 128;    // 128 échantillons = 2,56 s
constexpr int   N_CH           = 6;      // ax, ay, az [g], gx, gy, gz [deg/s] / 250
constexpr int   HOP            = 25;     // une inférence tous les 25 échantillons (0,5 s)
constexpr float GYRO_DIV       = 250.0f;
constexpr float FALL_THRESHOLD = 0.5f;

// Délai minimal entre deux alertes de chute (évite les alertes en rafale)
constexpr uint32_t ALERT_COOLDOWN_MS = 5000;

// ===== Bluetooth (mêmes valeurs que Config.java côté PC) =====
constexpr const char* DEVICE_NAME  = "ESP32C6-Demo";
constexpr const char* SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
constexpr const char* CHAR_UUID    = "beb5483e-36e1-4688-b7f5-ea07361b26a8";
