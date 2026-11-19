#include "fall_module.h"
#include "config.h"
#include "fall_weights.h"   // inclus ici uniquement (les poids ne doivent exister qu'une fois)
#include <Wire.h>
#include <math.h>

// Buffer circulaire des WINDOW derniers échantillons (rempli par la tâche d'échantillonnage)
static float ring[WINDOW][N_CH];
static volatile int ringPos = 0, filled = 0, newSamples = 0;
static portMUX_TYPE mux = portMUX_INITIALIZER_UNLOCKED;

// Tampons du CNN
static float input[WINDOW * N_CH];
static float bufA[124 * 16];
static float bufB[124 * 16];

// ===== MPU6050 =====
static void mpuWrite(uint8_t reg, uint8_t val) {
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(reg);
  Wire.write(val);
  Wire.endTransmission();
}

static int16_t readInt16() {
  int16_t hi = Wire.read();
  int16_t lo = Wire.read();
  return (hi << 8) | lo;
}

static bool mpuRead(float out[N_CH]) {
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(0x3B);  // ACCEL_XOUT_H
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom((uint8_t)MPU_ADDR, (uint8_t)14) != 14) return false;
  int16_t ax = readInt16(), ay = readInt16(), az = readInt16();
  readInt16();  // température, inutilisée
  int16_t gx = readInt16(), gy = readInt16(), gz = readInt16();
  out[0] = ax / 2048.0f;               // ±16 g -> 2048 LSB/g
  out[1] = ay / 2048.0f;
  out[2] = az / 2048.0f;
  out[3] = gx / 16.4f / GYRO_DIV;      // ±2000 deg/s -> 16,4 LSB/(deg/s)
  out[4] = gy / 16.4f / GYRO_DIV;
  out[5] = gz / 16.4f / GYRO_DIV;
  return true;
}

static void mpuInit() {
  mpuWrite(0x6B, 0x00);  // PWR_MGMT_1 : réveil
  delay(100);
  mpuWrite(0x1A, 0x04);  // CONFIG : filtre passe-bas ~20 Hz
  mpuWrite(0x19, 19);    // SMPLRT_DIV : 1 kHz / (1 + 19) = 50 Hz
  mpuWrite(0x1B, 0x18);  // GYRO_CONFIG : ±2000 deg/s
  mpuWrite(0x1C, 0x18);  // ACCEL_CONFIG : ±16 g
}

// ===== Tâche d'échantillonnage : un échantillon toutes les 20 ms =====
static void samplerTask(void*) {
  TickType_t last = xTaskGetTickCount();
  float s[N_CH];
  for (;;) {
    vTaskDelayUntil(&last, pdMS_TO_TICKS(1000 / FS_HZ));
    if (!mpuRead(s)) continue;
    portENTER_CRITICAL(&mux);
    memcpy(ring[ringPos], s, sizeof(s));
    ringPos = (ringPos + 1) % WINDOW;
    if (filled < WINDOW) filled++;
    newSamples++;
    portEXIT_CRITICAL(&mux);
  }
}

// ===== CNN (mêmes couches que le modèle Keras) =====
static int conv1dRelu(const float* in, int inLen, int inCh, const float* W, const float* B,
                      int k, int outCh, float* out) {
  int outLen = inLen - k + 1;
  for (int t = 0; t < outLen; t++) {
    for (int o = 0; o < outCh; o++) {
      float s = B[o];
      for (int j = 0; j < k; j++) {
        const float* x = in + (t + j) * inCh;
        const float* w = W + (j * inCh) * outCh + o;
        for (int i = 0; i < inCh; i++) s += x[i] * w[i * outCh];
      }
      out[t * outCh + o] = s > 0 ? s : 0;
    }
  }
  return outLen;
}

static int maxPool2(const float* in, int inLen, int ch, float* out) {
  int outLen = inLen / 2;
  for (int t = 0; t < outLen; t++)
    for (int c = 0; c < ch; c++)
      out[t * ch + c] = fmaxf(in[(2 * t) * ch + c], in[(2 * t + 1) * ch + c]);
  return outLen;
}

static float predict() {
  int len = conv1dRelu(input, WINDOW, N_CH, CONV1_W, CONV1_B, 5, 16, bufA);  // 124 x 16
  len = maxPool2(bufA, len, 16, bufB);                                        // 62 x 16
  len = conv1dRelu(bufB, len, 16, CONV2_W, CONV2_B, 5, 32, bufA);             // 58 x 32
  len = maxPool2(bufA, len, 32, bufB);                                        // 29 x 32
  len = conv1dRelu(bufB, len, 32, CONV3_W, CONV3_B, 3, 32, bufA);             // 27 x 32
  float z = DENSE_B[0];
  for (int c = 0; c < 32; c++) {                                              // moyenne globale + dense
    float mean = 0;
    for (int t = 0; t < len; t++) mean += bufA[t * 32 + c];
    z += (mean / len) * DENSE_W[c];
  }
  return 1.0f / (1.0f + expf(-z));                                            // sigmoïde
}

// ===== API du module =====
void fall_init() {
  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(400000);

  Wire.beginTransmission(MPU_ADDR);
  Wire.write(0x75);  // WHO_AM_I
  Wire.endTransmission(false);
  Wire.requestFrom((uint8_t)MPU_ADDR, (uint8_t)1);
  Serial.printf("MPU6050 WHO_AM_I = 0x%02X\n", Wire.available() ? Wire.read() : 0);

  mpuInit();
  xTaskCreate(samplerTask, "sampler", 4096, NULL, 5, NULL);
  Serial.println("Collecte des 2,56 premières secondes de données...");
}

bool fall_update(FallResult &r) {
  bool ready = false;
  portENTER_CRITICAL(&mux);
  if (filled == WINDOW && newSamples >= HOP) {
    newSamples = 0;
    for (int n = 0; n < WINDOW; n++)  // copie du plus ancien au plus récent
      memcpy(&input[n * N_CH], ring[(ringPos + n) % WINDOW], N_CH * sizeof(float));
    ready = true;
  }
  portEXIT_CRITICAL(&mux);
  if (!ready) return false;

  uint32_t t0 = micros();
  r.p = predict();
  r.us = micros() - t0;
  const float* lastSample = &input[(WINDOW - 1) * N_CH];
  r.acc[0] = lastSample[0];
  r.acc[1] = lastSample[1];
  r.acc[2] = lastSample[2];
  r.fall = r.p > FALL_THRESHOLD;
  return true;
}
