#include < ESP8266WiFi.h >
#include < PubSubClient.h >
#include < DHT.h >

#define PARAMETRO 1000
#define ANALOGINPUT A0
#define SOILMOISTURE 14 
#define LIGHT 2 
#define PUMP 13 
#define USERNAME "*******" 
#define KEY "*******"

const char* ssid = "*******";
omitido por segurança
const char* password = "*******";
omitido por segurança
const char* mqttServer = "smart.irrigation.com";
const char* mqttUser = USERNAME;
const char* mqttPassword = KEY;
const int mqttPort = 1883;

const char* mqttTopicSub = "smartirrigation/feeds/UmidadeSolo";
const char* mqttTopicSubDHT = "smartirrigation/feeds/Temperatura";
const char* mqttTopicSubTEMP = "smartirrigation/feeds/UmidadeAr";
const char* mqttTopicSubAuto = "smartirrigation/feeds/Automatico";
const char* mqttTopicSubLumi = "smartirrigation/feeds/Luminosidade";
const int hoursToPump = 12;
WiFiClient espClient;
PubSubClient client(mqttServer, mqttPort, espClient);
30
#define DHTPIN 4 
#define DHTTYPE DHT22
DHT dht(DHTPIN, DHTTYPE);
float UmidadeSolo = 0, umidadeDHT = 0, tempoDHT = 0,
    leituraLuminosidade = 0, last_Umi = 0;
char str1[8], str2[8], str3[8], str4[8];
unsigned long t = 0, t2 = 0, t_last = 0, t_luz = 0;
bool state = false, toggleIf = true;
bool ligou = true;
int TBOMBA = 3;

void setup() {
    Serial.begin(115200);
    pinMode(ANALOGINPUT, INPUT);
    pinMode(SOILMOISTURE, OUTPUT);
    pinMode(LIGHT, OUTPUT);
    pinMode(16, OUTPUT);
    pinMode(PUMP, OUTPUT);
    WiFi.mode(WIFI_AP_STA);
    WiFi.begin(ssid, password);
    Serial.println("Iniciando DHT");
    dht.begin();
    Serial.println("DHT Iniciado");
    Serial.println("Conectando na rede WiFi");
    while (WiFi.status() != WL_CONNECTED) {
        delay(1000);
        Serial.print(".");
    }
    Serial.println("Conectado com sucesso na rede WiFi");
    client.setKeepAlive(10);
    client.setSocketTimeout(10);
    ClientConnect();
    client.publish("smartirrigation/feeds/isAuto", "0", true);
    delay(2000);
    digitalWrite(LIGHT, HIGH);
}

void ClientConnect() {
    while (!client.connected()) {
        31
        delay(2000);
        Serial.println("Conectando ao Broker MQTT...");
        if (client.connect("ESP8266Client", mqttUser, mqttPassword)) {
            Serial.println("Conectado");
            client.subscribe("smartirrigation/feeds/Automatico");
        } else {
            Serial.print("Falha estado ");
            Serial.println(client.state());
            delay(5000);
        }
    }
}

void sensorsRead(){
    for (int i = 0; i <= 10; i++)
    {
        leituraLuminosidade = leituraLuminosidade + analogRead(ANALOGINPUT);
        delay(100);
    }
    digitalWrite(LIGHT, LOW);
    leituraLuminosidade = leituraLuminosidade / 10.0;
    leituraLuminosidade = 1250 - leituraLuminosidade;
    itoa(leituraLuminosidade, str4, 10);
    umidadeDHT = dht.readHumidity();
    itoa(umidadeDHT, str2, 10);
    tempoDHT = dht.readTemperature();
    itoa(tempoDHT, str3, 10);
    digitalWrite(SOILMOISTURE, HIGH);
    delay(100);
    for (int i = 0; i <= 10; i++)
    {
        UmidadeSolo = UmidadeSolo + analogRead(ANALOGINPUT);
        delay(10);
    }
    digitalWrite(SOILMOISTURE, LOW);
    UmidadeSolo = UmidadeSolo / 10.0;
    UmidadeSolo = map(UmidadeSolo, 200, 1250, 100, 0);
    itoa(UmidadeSolo, str1, 10);
    digitalWrite(LIGHT, HIGH);

    if (leituraLuminosidade > PARAMETRO && toggleIf) {
        t_luz = millis();
        toggleIf = false;
    }
    else if (leituraLuminosidade < PARAMETRO) {
        t_luz = 0;
        toggleIf = false;
        32
    }
}

void loop() {
    if (millis() - t > 30000) {
        sensorsRead();
        if (!client.connected()) {
            client.connect("ESP8266Client",
                mqttUser, mqttPassword);
        }
        client.publish("smartirrigation/feeds/UmidadeSolo", str1, true);
        client.publish("smartirrigation/feeds/UmidadeAr", str2, true);
        client.publish("smartirrigation/feeds/Temperatura", str3, true);
        client.publish("smartirrigation/feeds/Luminosidade", str4, true);
        delay(350);
        Serial.println("Sent Data.");
        t = millis();
    }
    if (millis() - t_last > hoursToPump * 60 * 60 * 1000 ||
        (UmidadeSolo < 15 && t_luz > 3600000)
        || ligou) {
        if (tempoDHT > 28 && leituraLuminosidade > 1000) TBOMBA += 1;
        if (UmidadeSolo < last_Umi) TBOMBA += 1;
        ligou = false;
        Serial.println("PUMP THEIR BUMP");
        if (!client.connected()) {
            client.connect("ESP8266Client",
                mqttUser, mqttPassword);
        }
        client.publish("smartirrigation/feeds/isAuto", "1", true);
        digitalWrite(PUMP, HIGH);
        delay(TBOMBA * 1000);
        if (!client.connected()) {
            client.connect("ESP8266Client",
                mqttUser, mqttPassword);
        }
        client.publish("smartirrigation/feeds/isAuto", "0", true);
        digitalWrite(PUMP, LOW);
        last_Umi = UmidadeSolo;
        TBOMBA = 3;
        t_last = millis();
    }
    delay(150);
}