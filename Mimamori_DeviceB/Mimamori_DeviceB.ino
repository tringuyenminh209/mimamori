#include <M5Stack.h>
#include <WiFi.h>
#include <PubSubClient.h>

// --- 設定  ---
const char* ssid = "C0p2Ec2-WLAN";
const char* password = "4Emah5LdS";
const char* mqtt_server = "broker.emqx.io";
const char* topic_name = "sk2a22/mimamori"; // トピック名はDevice Aと完全一致させること
// ----------------------------------------

WiFiClient espClient;
PubSubClient client(espClient);

// メッセージ受信時のコールバック関数
void callback(char* topic, byte* payload, unsigned int length) {
  String message = "";
  for (int i = 0; i < length; i++) {
    message += (char)payload[i];
  }
  
  M5.Lcd.setTextSize(3);
  M5.Lcd.setTextColor(BLACK);

  // メッセージ内容に応じた処理
  if (message == "ANZEN") { // 安全
    M5.Lcd.fillScreen(GREEN);
    M5.Lcd.setCursor(10, 100);
    M5.Lcd.print("ANZEN");
    M5.Speaker.mute(); // 音を消す
  } 
  else if (message == "CHUI") { // 注意
    M5.Lcd.fillScreen(YELLOW);
    M5.Lcd.setCursor(10, 100);
    M5.Lcd.print("CHUI");
    M5.Speaker.mute();
  } 
  else if (message == "KIKEN") { // 危険
    M5.Lcd.fillScreen(RED);
    M5.Lcd.setCursor(10, 100);
    M5.Lcd.print("KIKEN!");
    
    // 警告音を鳴らす
    M5.Speaker.setVolume(1);
    M5.Speaker.tone(1000, 500); 
  }
}

void setup_wifi() {
  delay(10);
  M5.Lcd.print("WiFi... ");
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    M5.Lcd.print(".");
  }
  M5.Lcd.println(" OK");
}

void reconnect() {
  while (!client.connected()) {
    String clientId = "M5B-" + String(random(0xffff), HEX);
    if (client.connect(clientId.c_str())) {
      M5.Lcd.println("MQTT Connected");
      client.subscribe(topic_name); // トピックを購読
    } else {
      delay(5000);
    }
  }
}

void setup() {
  M5.begin();
  M5.Power.begin();
  M5.Lcd.setTextSize(2);
  
  setup_wifi();
  client.setServer(mqtt_server, 1883);
  client.setCallback(callback);
}

void loop() {
  if (!client.connected()) reconnect();
  client.loop();

  M5.update();
  if (M5.BtnA.wasPressed()) { // ボタンAで消音
    M5.Speaker.mute();
    M5.Lcd.setCursor(10, 200);
    M5.Lcd.print("Muted");
  }
}
