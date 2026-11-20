#include "fall_event.h"

static const int MAX_EVENTS = 16;
static FallEvent events[MAX_EVENTS];
static int count = 0;          // nombre d'événements stockés
static int head = 0;           // prochaine case à écrire
static uint16_t nextId = 1;
static portMUX_TYPE logMux = portMUX_INITIALIZER_UNLOCKED;   // verrou : le BLE et loop() y accèdent

// Index du i-ème événement, du plus ancien (i = 0) au plus récent
static int indexOf(int i) { return (head - count + i + MAX_EVENTS) % MAX_EVENTS; }

void falllog_add(float p, uint32_t nowMs) {
  portENTER_CRITICAL(&logMux);
  FallEvent &e = events[head];
  e.id = nextId++;
  if (nextId == 0) nextId = 1;
  e.p = p;
  e.ms = nowMs;
  e.acked = false;
  e.sent = false;
  head = (head + 1) % MAX_EVENTS;
  if (count < MAX_EVENTS) count++;     // quand c'est plein, le plus ancien est écrasé
  portEXIT_CRITICAL(&logMux);
}

bool falllog_next_to_send(FallEvent &out) {
  bool found = false;
  portENTER_CRITICAL(&logMux);
  for (int i = 0; i < count && !found; i++) {
    FallEvent &e = events[indexOf(i)];
    if (!e.acked && !e.sent) {
      e.sent = true;
      out = e;
      found = true;
    }
  }
  portEXIT_CRITICAL(&logMux);
  return found;
}

void falllog_ack(uint16_t id) {
  portENTER_CRITICAL(&logMux);
  for (int i = 0; i < count; i++) {
    FallEvent &e = events[indexOf(i)];
    if (e.id == id) e.acked = true;
  }
  portEXIT_CRITICAL(&logMux);
}

void falllog_new_session() {
  portENTER_CRITICAL(&logMux);
  for (int i = 0; i < count; i++) events[indexOf(i)].sent = false;
  portEXIT_CRITICAL(&logMux);
}

int falllog_pending() {
  int n = 0;
  portENTER_CRITICAL(&logMux);
  for (int i = 0; i < count; i++) if (!events[indexOf(i)].acked) n++;
  portEXIT_CRITICAL(&logMux);
  return n;
}
