#include "ble_module.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "config.h"
#include "fall_event.h"

static String rxBuffer = "";
static volatile bool clientConnected = false;
static volatile bool clientReady = false;
static BLECharacteristic *txChar = nullptr;

static void handleLine(const String &line) {
  // --- Commandes envoyées par le PC ---
  if (line == "CMD=SYNC") {
    falllog_new_session();      // tout ce qui n'est pas confirmé sera renvoyé
    clientReady = true;
    Serial.println(">>> PC prêt : envoi des chutes enregistrées");
    return;
  }
  if (line.startsWith("ACK=")) {
    falllog_ack((uint16_t)line.substring(4).toInt());
    return;
  }

  // --- Autres messages (NAME=...;AGE=...;MSG=...) : affichés ---
  Serial.println("Reçu : " + line);
  int start = 0;
  while (start < (int)line.length()) {
    int end = line.indexOf(';', start);
    if (end < 0) end = line.length();
    String field = line.substring(start, end);
    int eq = field.indexOf('=');
    if (eq > 0) {
      Serial.println("  " + field.substring(0, eq) + " -> " + field.substring(eq + 1));
    }
    start = end + 1;
  }
}

class RxCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *c) {
    String chunk = c->getValue();
    rxBuffer += chunk;

    int nl;
    while ((nl = rxBuffer.indexOf('\n')) >= 0) {
      String line = rxBuffer.substring(0, nl);
      rxBuffer = rxBuffer.substring(nl + 1);
      line.trim();
      if (line.length() > 0) handleLine(line);
    }
  }
};

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *s) {
    clientConnected = true;
    clientReady = false;       // on attend que le PC envoie CMD=SYNC
    rxBuffer = "";
    Serial.println(">>> Client connecté");
  }
  void onDisconnect(BLEServer *s) {
    clientConnected = false;
    clientReady = false;
    Serial.println(">>> Client déconnecté");
    BLEDevice::startAdvertising();
  }
};

void ble_init() {
  BLEDevice::init(DEVICE_NAME);

  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService *service = server->createService(SERVICE_UUID);

  // PC -> ESP32 : écriture
  BLECharacteristic *rx = service->createCharacteristic(
      CHAR_UUID,
      BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  rx->setCallbacks(new RxCallbacks());

  // ESP32 -> PC : notification
  txChar = service->createCharacteristic(TX_CHAR_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  txChar->addDescriptor(new BLE2902());   // si l'IDE refuse cette ligne, supprime-la

  service->start();

  BLEAdvertising *adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(SERVICE_UUID);
  adv->setScanResponse(true);
  BLEDevice::startAdvertising();

  Serial.println("BLE prêt, en attente de connexion...");
}

bool ble_connected() { return clientConnected; }
bool ble_ready()     { return clientConnected && clientReady; }

void ble_send_line(const String &line) {
  if (!clientConnected || txChar == nullptr) return;

  String s = line + "\n";
  const int CHUNK = 20;   // taille sûre quel que soit le MTU
  for (int i = 0; i < (int)s.length(); i += CHUNK) {
    String part = s.substring(i, min((int)s.length(), i + CHUNK));
    txChar->setValue((uint8_t *)part.c_str(), part.length());
    txChar->notify();
    delay(10);
  }
}
