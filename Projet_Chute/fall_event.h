#pragma once
#include <Arduino.h>

struct FallEvent {
  uint16_t id;
  float    p;       // probabilité de chute
  uint32_t ms;      // millis() au moment de la chute
  bool     acked;   // true quand le PC a confirmé la réception
  bool     sent;    // true si déjà envoyé pendant la connexion en cours
};

void falllog_add(float p, uint32_t nowMs);          // enregistre une chute
bool falllog_next_to_send(FallEvent &out);          // prochaine chute à envoyer (la marque "envoyée")
void falllog_ack(uint16_t id);                      // le PC a bien reçu cette chute
void falllog_new_session();                         // nouvelle connexion : renvoie ce qui n'est pas confirmé
int  falllog_pending();                             // nombre de chutes pas encore confirmées
