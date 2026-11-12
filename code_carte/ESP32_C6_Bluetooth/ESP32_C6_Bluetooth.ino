#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>

// Mêmes valeurs que Config.java
#define DEVICE_NAME  "ESP32C6-Demo"
#define SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHAR_UUID    "beb5483e-36e1-4688-b7f5-ea07361b26a8"

String buffer = "";

// Découpe "NAME=...;VALUE=...;MSG=..." et affiche chaque champ
void handleLine(const String &line) {
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
    buffer += chunk;

    // Un message se termine par '\n' ; il peut arriver en plusieurs paquets
    int nl;
    while ((nl = buffer.indexOf('\n')) >= 0) {
      String line = buffer.substring(0, nl);
      buffer = buffer.substring(nl + 1);
      line.trim();
      if (line.length() > 0) handleLine(line);
    }
  }
};

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *s) {
    Serial.println(">>> Client connecté");
    buffer = "";
  }
  void onDisconnect(BLEServer *s) {
    Serial.println(">>> Client déconnecté");
    BLEDevice::startAdvertising();   // redevient visible
  }
};

void setup() {
  Serial.begin(115200);
  delay(1000);

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

void loop() {
  delay(1000);
}
