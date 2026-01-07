/**
  Date : 2025/12/10
  Author : NGUYEN MINH TRI
  Project: Mimamori (Smart Monitor System)
*/

#include <M5Stack.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include "M5UnitENV.h"
#include "Ambient.h"
#include <Adafruit_NeoPixel.h> // LED制御用

// --- ユーザー設定 (User Config) ---
const char* ssid = "C0p2Ec2-WLAN";
const char* password = "4Emah5LdS";
const char* mqtt_server = "broker.emqx.io";

// Topic設定 (重要: 送信用と受信用を分ける)
const char* topic_data = "sk2a22/data";    // 送信: センサデータ
const char* topic_control = "sk2a22/control"; // 受信: リモート操作

unsigned int channelId = 97377;          // Ambient Channel ID
const char* writeKey = "94699ddf84649123"; // Ambient Write Key

// LED設定 (Port B: GPIO 26)
#define LED_PIN 26
#define NUM_LEDS 3
// --------------------------------

WiFiClient espClient;
PubSubClient client(espClient);
Ambient ambient;
SHT3X sht3x;
Adafruit_NeoPixel pixels(NUM_LEDS, LED_PIN, NEO_GRB + NEO_KHZ800);

// 変数
bool remoteFanOn = false; // リモート操作状態
float lastTemp = 0.0;     // 前回の温度 (トレンド分析用)

// アバター描画関数 (修正版: fillArcを使わない)

void drawFace(int emotion) {

  int x = 160; int y = 120;

  M5.Lcd.fillRect(40, 80, 240, 100, BLACK); // 顔エリアクリア (黒)

  

  if (emotion == 0) { // 笑顔 (ANZEN)

    M5.Lcd.fillCircle(x - 50, y - 20, 10, WHITE); // 左目

    M5.Lcd.fillCircle(x + 50, y - 20, 10, WHITE); // 右目

    // 口 (笑顔)

    M5.Lcd.fillRect(x - 40, y + 20, 80, 10, WHITE);

    M5.Lcd.fillRect(x - 40, y + 20, 10, 5, BLACK); // 角を削って丸く見せる工夫

    M5.Lcd.fillRect(x + 30, y + 20, 10, 5, BLACK);

  } 

  else if (emotion == 1) { // 真顔 (CHUI)

    M5.Lcd.fillCircle(x - 50, y - 20, 10, YELLOW);

    M5.Lcd.fillCircle(x + 50, y - 20, 10, YELLOW);

    // 口 (真顔)

    M5.Lcd.fillRect(x - 40, y + 20, 80, 5, YELLOW); 

  }

  else if (emotion == 2) { // 泣き顔 (KIKEN)

    M5.Lcd.fillCircle(x - 50, y - 20, 10, RED);

    M5.Lcd.fillCircle(x + 50, y - 20, 10, RED);

    // 口 (への字)

    M5.Lcd.drawLine(x - 40, y + 40, x, y + 20, RED);

    M5.Lcd.drawLine(x, y + 20, x + 40, y + 40, RED);

    M5.Lcd.fillCircle(x + 70, y - 10, 5, BLUE); // 涙

  }

  else if (emotion == 3) { // クール (REMOTE)

    M5.Lcd.fillRect(x - 70, y - 30, 140, 25, CYAN); // サングラス

    // 口 (笑顔)

    M5.Lcd.fillRect(x - 40, y + 20, 80, 10, WHITE);

  }

}

// LEDファン制御関数
void setFan(bool state, uint32_t color) {
  if (state) {
    for(int i=0; i<NUM_LEDS; i++) pixels.setPixelColor(i, color);
  } else {
    for(int i=0; i<NUM_LEDS; i++) pixels.setPixelColor(i, pixels.Color(0,0,0));
  }
  pixels.show();
}

// MQTT受信コールバック (リモート操作)
void callback(char* topic, byte* payload, unsigned int length) {
  String msg = "";
  for (int i = 0; i < length; i++) msg += (char)payload[i];
  
  if (msg == "FAN_ON") {
    remoteFanOn = true;
    M5.Lcd.setCursor(10, 220); M5.Lcd.print("REMOTE: ON ");
  } 
  else if (msg == "FAN_OFF") {
    remoteFanOn = false;
    M5.Lcd.setCursor(10, 220); M5.Lcd.print("REMOTE: OFF");
  }
}

void setup_wifi() {
  delay(10);
  M5.Lcd.print("Connecting to WiFi");
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    M5.Lcd.print(".");
  }
  M5.Lcd.println("\nWiFi Connected!");
}

void reconnect() {
  while (!client.connected()) {
    String clientId = "M5A-" + String(random(0xffff), HEX);
    if (client.connect(clientId.c_str())) {
      client.subscribe(topic_control); // 制御トピックを購読
    } else {
      delay(5000);
    }
  }
}

float calculateDI(float t, float h) {
  return 0.81 * t + 0.01 * h * (0.99 * t - 14.3) + 46.3;
}

void setup() {
  M5.begin();
  M5.Power.begin();
  M5.Lcd.setTextSize(2);
  pixels.begin(); // LED初期化

  if (!sht3x.begin(&Wire, 0x44, 21, 22, 400000)) {
    M5.Lcd.println("Sensor Error!"); while (1);
  }

  setup_wifi();
  client.setServer(mqtt_server, 1883);
  client.setCallback(callback); // コールバック登録
  ambient.begin(channelId, writeKey, &espClient);
}

void loop() {
  if (!client.connected()) reconnect();
  client.loop(); // MQTT受信処理

  if (sht3x.update()) {
    float temp = sht3x.cTemp;
    float hum = sht3x.humidity;
    float di = calculateDI(temp, hum);

    // トレンド分析 (急上昇検知)
    bool heatSpike = (temp - lastTemp > 2.0); 
    lastTemp = temp;

    // 状態判定
    String status = "ANZEN";
    uint16_t bg = GREEN;
    int face = 0;
    
    // ファン制御ロジック (自動 OR リモート)
    bool fanState = false;
    uint32_t fanColor = pixels.Color(0,0,0);

    if (remoteFanOn) {
      status = "REMOTE";
      bg = CYAN;
      face = 3; // クール顔
      fanState = true;
      fanColor = pixels.Color(0, 255, 0); // 緑 (リモート中)
    }
    else if (di >= 80 || heatSpike) {
      status = "KIKEN";
      bg = RED;
      face = 2; // 泣き顔
      fanState = true;
      fanColor = pixels.Color(255, 0, 0); // 赤 (危険)
    }
    else if (di >= 75) {
      status = "CHUI";
      bg = YELLOW;
      face = 1; // 真顔
      fanState = true;
      fanColor = pixels.Color(0, 0, 255); // 青 (弱)
    }

    // 画面更新
    M5.Lcd.fillScreen(bg);
    M5.Lcd.setTextColor(BLACK);
    M5.Lcd.setCursor(10, 10);
    M5.Lcd.printf("Temp: %.1f C  Hum: %.1f %%", temp, hum);
    M5.Lcd.setCursor(10, 40);
    M5.Lcd.printf("DI: %.1f  [%s]", di, status.c_str());
    
    if (heatSpike) {
      M5.Lcd.setCursor(160, 40);
      M5.Lcd.setTextColor(RED);
      M5.Lcd.print("SPIKE!"); // 急上昇警告
    }

    drawFace(face); // 顔を描画
    setFan(fanState, fanColor); // LED制御

    // データ送信
    client.publish(topic_data, status.c_str());
    
    ambient.set(1, temp);
    ambient.set(2, hum);
    ambient.set(3, di);
    ambient.send();
  }
  
  delay(2000); // 2秒間隔
}