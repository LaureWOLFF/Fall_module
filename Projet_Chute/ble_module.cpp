#include "ble_module.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include "config.h"   // après les en-têtes BLE

static String rxBuffer = "";
static volatile bool clientConnected = false;

// Découpe "NAME=...;VALUE=...;MSG=..." et affiche chaque champ
static void handleLine(const String &line) {
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

    // Un message se termine par '\n' ; il peut arriver en plusieurs paquets
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
    rxBuffer = "";
    Serial.println(">>> Client connecté");
  }
  void onDisconnect(BLEServer *s) {
    clientConnected = false;
    Serial.println(">>> Client déconnecté");
    BLEDevice::startAdvertising();   // redevient visible
  }
};

void ble_init() {
  BLEDevice::init(DEVICE_NAME);

  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService *service = server->createService(SERVICE_UUID);
  BLECharacteristic *ch = service->createCharacteristic(
      CHAR_UUID,
      BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  ch->setCallbacks(new RxCallbacks());
  service->start();

  BLEAdvertising *adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(SERVICE_UUID);
  adv->setScanResponse(true);        // le nom part dans la réponse de scan
  BLEDevice::startAdvertising();

  Serial.println("BLE prêt, en attente de connexion...");
}

bool ble_connected() { return clientConnected; }
