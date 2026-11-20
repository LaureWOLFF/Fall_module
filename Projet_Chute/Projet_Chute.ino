#include "config.h"
#include "fall_module.h"
#include "fall_event.h"
#include "ble_module.h"

static uint32_t lastFallMs = 0;
static bool hasFall = false;

// Envoie une chute enregistrée au PC, seulement s'il est connecté et prêt.
static void sendPendingFall() {
  if (!ble_ready()) return;

  FallEvent e;
  if (!falllog_next_to_send(e)) return;     // rien à envoyer

  char msg[64];
  snprintf(msg, sizeof(msg), "EVT=FALL;ID=%u;P=%.3f;AGO=%lu",
           (unsigned)e.id, e.p, (unsigned long)((millis() - e.ms) / 1000));
  ble_send_line(msg);
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  fall_init();
  ble_init();
}

void loop() {
  sendPendingFall();            // ne fait rien s'il n'y a rien à envoyer

  FallResult r;
  if (!fall_update(r)) {
    delay(5);
    return;
  }

  Serial.printf("p=%.3f  acc=[%+.2f %+.2f %+.2f] g  inference=%lu us%s\n",
                r.p, r.acc[0], r.acc[1], r.acc[2], (unsigned long)r.us,
                r.fall ? "  <-- FALL DETECTED" : "");

  // Enregistre la chute (au plus une toutes les FALL_COOLDOWN_MS), connecté ou non
  if (r.fall && (!hasFall || millis() - lastFallMs >= FALL_COOLDOWN_MS)) {
    hasFall = true;
    lastFallMs = millis();
    falllog_add(r.p, millis());
    Serial.printf("Chute enregistrée (%d en attente d'envoi)\n", falllog_pending());
  }
}
