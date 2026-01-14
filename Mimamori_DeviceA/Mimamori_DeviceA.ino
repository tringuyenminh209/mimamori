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
#include <time.h> // NTP時刻取得用
#include <math.h> // 数学関数（sin, cos用）

// --- ユーザー設定 (User Config) ---
#ifndef PI
#define PI 3.14159265359
#endif

const char* ssid = "C0p2Ec2-WLAN";
const char* password = "4Emah5LdS";
const char* mqtt_server = "broker.emqx.io";

// NTP設定 (日本時間: JST = UTC+9)
const char* ntpServer = "pool.ntp.org";
const long gmtOffset_sec = 9 * 3600; // JST (UTC+9)
const int daylightOffset_sec = 0;

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
bool timeInitialized = false; // NTP時刻初期化フラグ

// 季節エフェクト用変数
struct SnowFlake {
  int x;
  int y;
  int speed;
};

struct Petal {
  int x;
  int y;
  float speed;
  float angle;
};

struct Leaf {
  int x;
  int y;
  float speed;
  float angle;
  int size;
};

SnowFlake snowflakes[20];  // 雪の粒子
Petal petals[15];           // 桜の花びら
Leaf leaves[12];             // 落ち葉
int animationFrame = 0;      // アニメーションフレーム

// アバター描画関数 (優しい表情、豊かな表現)

void drawFace(int emotion, uint16_t bgColor) {
  // 顔の位置とサイズ
  int x = 160; int y = 180;
  int faceRadius = 45;
  int eyeOffsetX = 18;
  int eyeOffsetY = -12;
  int eyeSize = 8;  // 目を少し大きく
  int mouthWidth = 30;

  // 顔の色（柔らかい色調）
  uint16_t faceColor = 0xF7E7;  // 薄いピンク（WHITEより優しい）
  if (emotion == 1) faceColor = 0xFFE0;  // 薄い黄色
  else if (emotion == 2) faceColor = 0xFFA0;  // 薄い赤
  else if (emotion == 3) faceColor = 0x9FFF;  // 薄いシアン
  else if (emotion == 4) faceColor = 0x9EFF;  // 薄い青
  
  // 顔の輪郭（丸い、柔らかい）
  M5.Lcd.fillCircle(x, y, faceRadius, faceColor);
  
  // 頬の赤み（笑顔の時）
  if (emotion == 0) {
    M5.Lcd.fillCircle(x - 25, y + 5, 6, 0xFF80);  // 左頬（ピンク）
    M5.Lcd.fillCircle(x + 25, y + 5, 6, 0xFF80); // 右頬（ピンク）
  }

  if (emotion == 0) { // 笑顔 (ANZEN) - 明るく優しい
    // 眉毛（優しい弧）
    M5.Lcd.drawLine(x - eyeOffsetX - 3, y + eyeOffsetY - 8, x - eyeOffsetX + 3, y + eyeOffsetY - 6, BLACK);
    M5.Lcd.drawLine(x + eyeOffsetX - 3, y + eyeOffsetY - 8, x + eyeOffsetX + 3, y + eyeOffsetY - 6, BLACK);
    
    // 目（大きく、優しい、ハイライト付き）
    M5.Lcd.fillCircle(x - eyeOffsetX, y + eyeOffsetY, eyeSize, BLACK);
    M5.Lcd.fillCircle(x + eyeOffsetX, y + eyeOffsetY, eyeSize, BLACK);
    // 目のハイライト（生き生きと）
    M5.Lcd.fillCircle(x - eyeOffsetX - 2, y + eyeOffsetY - 2, 3, WHITE);
    M5.Lcd.fillCircle(x + eyeOffsetX - 2, y + eyeOffsetY - 2, 3, WHITE);
    
    // 口（大きな笑顔、弧を描く）
    for(int i = 0; i < mouthWidth; i++) {
      int px = x - mouthWidth/2 + i;
      int offset = (i - mouthWidth/2) * (i - mouthWidth/2) / 8;  // より大きな弧
      int py = y + 15 + offset;
      if (py < y + 25) {
        M5.Lcd.fillCircle(px, py, 2, BLACK);
      }
    }
  } 
  else if (emotion == 1) { // 真顔 (CHUI) - 真剣だが優しい
    // 眉毛（少し下がった）
    M5.Lcd.drawLine(x - eyeOffsetX - 3, y + eyeOffsetY - 6, x - eyeOffsetX + 3, y + eyeOffsetY - 6, BLACK);
    M5.Lcd.drawLine(x + eyeOffsetX - 3, y + eyeOffsetY - 6, x + eyeOffsetX + 3, y + eyeOffsetY - 6, BLACK);
    
    // 目（普通の大きさ、ハイライト付き）
    M5.Lcd.fillCircle(x - eyeOffsetX, y + eyeOffsetY, eyeSize - 1, BLACK);
    M5.Lcd.fillCircle(x + eyeOffsetX, y + eyeOffsetY, eyeSize - 1, BLACK);
    M5.Lcd.fillCircle(x - eyeOffsetX - 1, y + eyeOffsetY - 1, 2, WHITE);
    M5.Lcd.fillCircle(x + eyeOffsetX - 1, y + eyeOffsetY - 1, 2, WHITE);
    
    // 口（真っ直ぐ、優しい）
    M5.Lcd.fillRect(x - mouthWidth/2, y + 18, mouthWidth, 3, BLACK);
  }
  else if (emotion == 2) { // 悲しい顔 (KIKEN) - 心配そうだが優しい
    // 眉毛（八の字、心配そう）
    M5.Lcd.drawLine(x - eyeOffsetX - 2, y + eyeOffsetY - 8, x - eyeOffsetX + 4, y + eyeOffsetY - 5, BLACK);
    M5.Lcd.drawLine(x + eyeOffsetX - 4, y + eyeOffsetY - 8, x + eyeOffsetX + 2, y + eyeOffsetY - 5, BLACK);
    
    // 目（少し大きく、ハイライト付き）
    M5.Lcd.fillCircle(x - eyeOffsetX, y + eyeOffsetY, eyeSize, BLACK);
    M5.Lcd.fillCircle(x + eyeOffsetX, y + eyeOffsetY, eyeSize, BLACK);
    M5.Lcd.fillCircle(x - eyeOffsetX - 2, y + eyeOffsetY - 2, 2, WHITE);
    M5.Lcd.fillCircle(x + eyeOffsetX - 2, y + eyeOffsetY - 2, 2, WHITE);
    
    // 口（への字、優しい）
    for(int i = 0; i < mouthWidth; i++) {
      int px = x - mouthWidth/2 + i;
      int offset = (mouthWidth/2 - abs(i - mouthWidth/2)) * 2;  // への字
      int py = y + 18 + offset;
      if (py < y + 28) {
        M5.Lcd.fillCircle(px, py, 2, BLACK);
      }
    }
    
    // 涙（優しい）
    M5.Lcd.fillCircle(x + eyeOffsetX + 3, y + eyeOffsetY + 8, 3, 0x07FF); // 薄い青
  }
  else if (emotion == 3) { // クール (REMOTE) - かっこいい笑顔
    // サングラス（丸みを帯びた）
    M5.Lcd.fillCircle(x - 20, y + eyeOffsetY, 12, CYAN);
    M5.Lcd.fillCircle(x + 20, y + eyeOffsetY, 12, CYAN);
    M5.Lcd.fillCircle(x - 20, y + eyeOffsetY, 10, BLACK);
    M5.Lcd.fillCircle(x + 20, y + eyeOffsetY, 10, BLACK);
    // サングラスのつなぎ
    M5.Lcd.fillRect(x - 8, y + eyeOffsetY - 2, 16, 4, CYAN);
    
    // 口（大きな笑顔）
    for(int i = 0; i < mouthWidth; i++) {
      int px = x - mouthWidth/2 + i;
      int offset = (i - mouthWidth/2) * (i - mouthWidth/2) / 8;
      int py = y + 15 + offset;
      if (py < y + 25) {
        M5.Lcd.fillCircle(px, py, 2, BLACK);
      }
    }
  }
  else if (emotion == 4) { // 震える顔 (SAMUI) - 寒そうだが優しい
    // 眉毛（少し上がった、心配そう）
    M5.Lcd.drawLine(x - eyeOffsetX - 2, y + eyeOffsetY - 9, x - eyeOffsetX + 2, y + eyeOffsetY - 7, BLACK);
    M5.Lcd.drawLine(x + eyeOffsetX - 2, y + eyeOffsetY - 9, x + eyeOffsetX + 2, y + eyeOffsetY - 7, BLACK);
    
    // 目（大きく開いた、ハイライト付き）
    M5.Lcd.fillCircle(x - eyeOffsetX, y + eyeOffsetY, eyeSize, BLACK);
    M5.Lcd.fillCircle(x + eyeOffsetX, y + eyeOffsetY, eyeSize, BLACK);
    M5.Lcd.fillCircle(x - eyeOffsetX - 2, y + eyeOffsetY - 2, 3, WHITE);
    M5.Lcd.fillCircle(x + eyeOffsetX - 2, y + eyeOffsetY - 2, 3, WHITE);
    
    // 口（震える、優しい）
    for(int i = 0; i < 6; i++) {
      int px = x - mouthWidth/2 + i * (mouthWidth/5);
      int wave = (i % 2 == 0) ? 2 : -2;  // 波打つ
      M5.Lcd.drawLine(px, y + 18, px + 2, y + 25 + wave, 0x07FF);
    }
    
    // 頬の青み（寒そう）
    M5.Lcd.fillCircle(x - 28, y + 3, 5, 0x07FF);
    M5.Lcd.fillCircle(x + 28, y + 3, 5, 0x07FF);
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

// 季節判定関数 (月ベース)
String getSeason(int month) {
  // 12, 1, 2月 = 冬 
  if (month == 12 || month == 1 || month == 2) {
    return "Fuyu"; // 冬
  }
  // 6, 7, 8月 = 夏 
  else if (month >= 6 && month <= 8) {
    return "Natsu"; // 夏
  }
  // 3, 4, 5月 = 春 
  else if (month >= 3 && month <= 5) {
    return "Haru"; // 春
  }
  // 9, 10, 11月 = 秋
  else {
    return "Aki"; // 秋
  }
}

// 季節エフェクト初期化
void initSeasonEffects(String season) {
  if (season == "Fuyu") {
    // 雪の粒子を初期化
    for(int i = 0; i < 20; i++) {
      snowflakes[i].x = random(0, 320);
      snowflakes[i].y = random(-50, 0);
      snowflakes[i].speed = random(1, 4);
    }
  }
  else if (season == "Haru") {
    // 桜の花びらを初期化
    for(int i = 0; i < 15; i++) {
      petals[i].x = random(0, 320);
      petals[i].y = random(-30, 0);
      petals[i].speed = random(10, 30) / 10.0;
      petals[i].angle = random(0, 360) * PI / 180.0;
    }
  }
  else if (season == "Aki") {
    // 落ち葉を初期化
    for(int i = 0; i < 12; i++) {
      leaves[i].x = random(0, 320);
      leaves[i].y = random(-40, 0);
      leaves[i].speed = random(8, 20) / 10.0;
      leaves[i].angle = random(0, 360) * PI / 180.0;
      leaves[i].size = random(3, 6);
    }
  }
}

// 冬の雪エフェクト
void drawWinterEffect(uint16_t bgColor) {
  for(int i = 0; i < 20; i++) {
    // 雪を描画（白）
    M5.Lcd.fillCircle(snowflakes[i].x, snowflakes[i].y, 2, WHITE);
    
    // 雪を降らす
    snowflakes[i].y += snowflakes[i].speed;
    
    // 画面外に出たら上から再生成
    if (snowflakes[i].y > 240) {
      snowflakes[i].x = random(0, 320);
      snowflakes[i].y = random(-50, 0);
      snowflakes[i].speed = random(1, 4);
    }
    
    // 左右に少し揺れる
    snowflakes[i].x += sin(animationFrame * 0.1 + i) * 0.5;
    if (snowflakes[i].x < 0) snowflakes[i].x = 320;
    if (snowflakes[i].x > 320) snowflakes[i].x = 0;
  }
}

// 春の桜エフェクト
void drawSpringEffect(uint16_t bgColor) {
  for(int i = 0; i < 15; i++) {
    // 桜の花びらを描画（ピンク）
    int px = petals[i].x + cos(petals[i].angle) * 3;
    int py = petals[i].y;
    M5.Lcd.fillCircle(px, py, 2, 0xF81F); // ピンク
    
    // 花びらを降らす（回転しながら）
    petals[i].y += petals[i].speed;
    petals[i].angle += 0.1;
    petals[i].x += sin(petals[i].angle) * 2;
    
    // 画面外に出たら上から再生成
    if (petals[i].y > 240) {
      petals[i].x = random(0, 320);
      petals[i].y = random(-30, 0);
      petals[i].speed = random(10, 30) / 10.0;
      petals[i].angle = random(0, 360) * PI / 180.0;
    }
    if (petals[i].x < 0) petals[i].x = 320;
    if (petals[i].x > 320) petals[i].x = 0;
  }
}

// 夏の太陽と雲エフェクト
void drawSummerEffect(uint16_t bgColor) {
  // 太陽（右上）
  int sunX = 280;
  int sunY = 30;
  int sunSize = 25 + sin(animationFrame * 0.1) * 2; // 脈動
  M5.Lcd.fillCircle(sunX, sunY, sunSize, YELLOW);
  
  // 太陽の光線
  for(int i = 0; i < 8; i++) {
    float angle = i * PI / 4 + animationFrame * 0.05;
    int rayX = sunX + cos(angle) * (sunSize + 5);
    int rayY = sunY + sin(angle) * (sunSize + 5);
    M5.Lcd.drawLine(sunX, sunY, rayX, rayY, YELLOW);
  }
  
  // 雲（上部）
  int cloudX1 = 50 + sin(animationFrame * 0.02) * 10;
  int cloudY1 = 20;
  M5.Lcd.fillCircle(cloudX1, cloudY1, 8, WHITE);
  M5.Lcd.fillCircle(cloudX1 + 10, cloudY1, 10, WHITE);
  M5.Lcd.fillCircle(cloudX1 + 20, cloudY1, 8, WHITE);
  
  int cloudX2 = 200 + sin(animationFrame * 0.03 + 1) * 8;
  int cloudY2 = 15;
  M5.Lcd.fillCircle(cloudX2, cloudY2, 7, WHITE);
  M5.Lcd.fillCircle(cloudX2 + 8, cloudY2, 9, WHITE);
  M5.Lcd.fillCircle(cloudX2 + 16, cloudY2, 7, WHITE);
}

// 秋の落ち葉エフェクト
void drawAutumnEffect(uint16_t bgColor) {
  for(int i = 0; i < 12; i++) {
    // 落ち葉を描画（オレンジ/茶色）
    uint16_t leafColor = (i % 2 == 0) ? 0xFC00 : 0x8200; // オレンジ or 茶色
    int px = leaves[i].x + cos(leaves[i].angle) * leaves[i].size;
    int py = leaves[i].y;
    
    // 葉っぱの形（小さな楕円）
    M5.Lcd.fillCircle(px, py, leaves[i].size, leafColor);
    M5.Lcd.fillCircle(px - 1, py - 1, leaves[i].size - 1, leafColor);
    
    // 葉っぱを降らす（回転しながら）
    leaves[i].y += leaves[i].speed;
    leaves[i].angle += 0.15;
    leaves[i].x += sin(leaves[i].angle) * 3;
    
    // 画面外に出たら上から再生成
    if (leaves[i].y > 240) {
      leaves[i].x = random(0, 320);
      leaves[i].y = random(-40, 0);
      leaves[i].speed = random(8, 20) / 10.0;
      leaves[i].angle = random(0, 360) * PI / 180.0;
      leaves[i].size = random(3, 6);
    }
    if (leaves[i].x < -10) leaves[i].x = 330;
    if (leaves[i].x > 330) leaves[i].x = -10;
  }
}

// 季節エフェクト描画
void drawSeasonEffect(String season, uint16_t bgColor) {
  animationFrame++;
  
  if (season == "Fuyu") {
    drawWinterEffect(bgColor);
  }
  else if (season == "Haru") {
    drawSpringEffect(bgColor);
  }
  else if (season == "Natsu") {
    drawSummerEffect(bgColor);
  }
  else if (season == "Aki") {
    drawAutumnEffect(bgColor);
  }
}

// 日時取得・表示関数 (背景色に応じてテキスト色を調整)
void updateDateTime(uint16_t bgColor) {
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) {
    // 時刻取得失敗時は表示しない
    return;
  }
  
  // 背景色に応じてテキスト色を決定
  uint16_t textColor = BLACK;
  uint16_t seasonColor = CYAN;
  
  // 暗い背景の場合は白文字
  if (bgColor == RED || bgColor == BLUE || bgColor == GREEN) {
    textColor = WHITE;
    seasonColor = YELLOW;
  }
  // 明るい背景の場合は黒文字
  else if (bgColor == YELLOW || bgColor == CYAN) {
    textColor = BLACK;
    seasonColor = BLUE;
  }
  
  // 日時表示 (上部左側、大きめのフォント)
  M5.Lcd.setTextSize(2);
  M5.Lcd.setTextColor(textColor);
  M5.Lcd.setCursor(10, 10);
  M5.Lcd.printf("%04d/%02d/%02d %02d:%02d:%02d", 
                timeinfo.tm_year + 1900, 
                timeinfo.tm_mon + 1, 
                timeinfo.tm_mday,
                timeinfo.tm_hour, 
                timeinfo.tm_min, 
                timeinfo.tm_sec);
  
  // 季節表示 (日付の下、大きめのフォント)
  String season = getSeason(timeinfo.tm_mon + 1);
  M5.Lcd.setCursor(10, 35);
  M5.Lcd.setTextColor(seasonColor);
  M5.Lcd.setTextSize(2);
  M5.Lcd.printf("[%s]", season.c_str());
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
  
  // NTP時刻設定
  configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
  M5.Lcd.println("Setting time from NTP...");
  delay(1000);
  
  // 季節エフェクト初期化
  struct tm timeinfo;
  if (getLocalTime(&timeinfo)) {
    String season = getSeason(timeinfo.tm_mon + 1);
    initSeasonEffects(season);
  }
  
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

    // 状態判定 (優先順位: リモート > 温度 > DI)
    String status = "ANZEN";
    uint16_t bg = GREEN;
    int face = 0;
    
    // ファン/暖房制御ロジック (自動 OR リモート)
    bool fanState = false;
    uint32_t fanColor = pixels.Color(0,0,0);

    if (remoteFanOn) {
      // 最優先: リモート制御
      status = "REMOTE";
      bg = CYAN;
      face = 3; // クール顔
      fanState = true;
      fanColor = pixels.Color(0, 255, 0); // 緑 (リモート中)
    }
    else if (temp < 18.0) {
      // 第2優先: 寒い状態 (SAMUI)
      status = "SAMUI";
      bg = BLUE; // 青背景 (警告)
      face = 4; // 震える顔
      fanState = true;
      fanColor = pixels.Color(255, 100, 0); // オレンジ (暖房)
    }
    else if (di >= 80 || heatSpike) {
      // 第3優先: 危険状態 (KIKEN)
      status = "KIKEN";
      bg = RED;
      face = 2; // 泣き顔
      fanState = true;
      fanColor = pixels.Color(255, 0, 0); // 赤 (危険)
    }
    else if (di >= 75) {
      // 第4優先: 注意状態 (CHUI)
      status = "CHUI";
      bg = YELLOW;
      face = 1; // 真顔
      fanState = true;
      fanColor = pixels.Color(0, 0, 255); // 青 (弱)
    }
    // デフォルト: ANZEN (DI < 75 && temp >= 18°C)

    // 画面更新
    M5.Lcd.fillScreen(bg);
    
    // 季節エフェクトを描画（背景の上に）
    struct tm timeinfo;
    if (getLocalTime(&timeinfo)) {
      String season = getSeason(timeinfo.tm_mon + 1);
      drawSeasonEffect(season, bg);
    }
    
    // 背景色に応じてテキスト色を決定
    uint16_t textColor = BLACK;
    if (bg == RED || bg == BLUE || bg == GREEN) {
      textColor = WHITE;
    }
    
    // 日時・季節表示 (上部、背景色を渡す)
    updateDateTime(bg);
    
    // センサー情報表示 (日時の下に配置)
    M5.Lcd.setTextSize(2);
    M5.Lcd.setTextColor(textColor);
    M5.Lcd.setCursor(10, 65);
    M5.Lcd.printf("Temp: %.1f C  Hum: %.1f %%", temp, hum);
    M5.Lcd.setCursor(10, 95);
    M5.Lcd.printf("DI: %.1f  [%s]", di, status.c_str());
    
    if (heatSpike) {
      M5.Lcd.setCursor(200, 95);
      M5.Lcd.setTextColor(RED);
      M5.Lcd.print("SPIKE!"); // 急上昇警告
    }

    drawFace(face, bg); // 顔を描画（背景色を渡す）
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