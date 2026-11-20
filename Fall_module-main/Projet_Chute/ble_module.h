#pragma once
#include <Arduino.h>

// Démarre le serveur BLE (service + caractéristique en écriture) et l'annonce.
void ble_init();

// true si un client (le PC) est connecté.
bool ble_connected();
