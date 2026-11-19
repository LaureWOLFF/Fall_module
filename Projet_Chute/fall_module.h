#pragma once
#include <Arduino.h>

struct FallResult {
  float    p;        // probabilité de chute (0..1)
  float    acc[3];   // dernière accélération ax, ay, az [g]
  uint32_t us;       // durée de l'inférence en microsecondes
  bool     fall;     // true si p > FALL_THRESHOLD
};

// Initialise l'I2C, le MPU6050 et la tâche d'échantillonnage (50 Hz).
void fall_init();

// À appeler souvent dans loop(). Retourne true quand une nouvelle
// inférence vient d'être calculée (résultat dans r), sinon false.
bool fall_update(FallResult &r);
