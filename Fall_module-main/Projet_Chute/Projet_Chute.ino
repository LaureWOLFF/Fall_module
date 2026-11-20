#include "config.h"
#include "fall_module.h"
#include "ble_module.h"

static uint32_t lastAlertMs = 0;

// Appelée quand une chute est détectée (au plus une fois par ALERT_COOLDOWN_MS).
// C'est ici qu'on branchera plus tard l'envoi de l'alerte vers le PC.
static void onFall(const FallResult &r) {
  Serial.printf("*** ALERTE CHUTE ***  p=%.3f  (client BLE : %s)\n",
                r.p, ble_connected() ? "connecté" : "non connecté");
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  fall_init();   // capteur + échantillonnage 50 Hz
  ble_init();    // Bluetooth
}

void loop() {
  FallResult r;
  if (!fall_update(r)) {   // pas encore de nouvelle fenêtre à analyser
    delay(5);
    return;
  }

  Serial.printf("p=%.3f  acc=[%+.2f %+.2f %+.2f] g  gyr=[%+7.1f %+7.1f %+7.1f] deg/s  inference=%lu us%s\n",
                r.p, r.acc[0], r.acc[1], r.acc[2], r.gyr[0], r.gyr[1], r.gyr[2],
                (unsigned long)r.us, r.fall ? "  <-- FALL DETECTED" : "");

  if (r.fall && (millis() - lastAlertMs >= ALERT_COOLDOWN_MS || lastAlertMs == 0)) {
    lastAlertMs = millis();
    onFall(r);
  }
}
