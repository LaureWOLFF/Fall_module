#pragma once
#include <Arduino.h>

void ble_init();
bool ble_connected();                       // un PC est connecté
bool ble_ready();                           // connecté ET le PC a envoyé CMD=SYNC
void ble_send_line(const String &line);     // envoie une ligne au PC (notification)
