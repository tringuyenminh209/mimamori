package ecccomp.s2240788.mimamori.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttService(private val context: Context) {
    private var mqttClient: MqttClient? = null
    private var brokerUrl: String = "wss://broker.emqx.io:8084/mqtt"
    private var topicData: String = "sk2a22/data"
    private var topicControl: String = "sk2a22/control"
    private val scope = CoroutineScope(Dispatchers.IO)
    
    var onMessageReceived: ((String, String) -> Unit)? = null
    var onConnectionStatusChanged: ((Boolean) -> Unit)? = null
    
    fun connect(broker: String, dataTopic: String, controlTopic: String) {
        brokerUrl = broker
        topicData = dataTopic
        topicControl = controlTopic
        
        val clientId = "mimamori-android-${System.currentTimeMillis()}"
        scope.launch {
            try {
                mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())
                
                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    isAutomaticReconnect = true
                    connectionTimeout = 10
                    keepAliveInterval = 60
                }
                
                mqttClient?.connect(options)
                Log.d("MqttService", "Connected to MQTT broker")
                onConnectionStatusChanged?.invoke(true)
                subscribeToTopics()
                
                mqttClient?.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        Log.w("MqttService", "Connection lost", cause)
                        onConnectionStatusChanged?.invoke(false)
                    }
                    
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        topic?.let { t ->
                            message?.let { m ->
                                val payload = String(m.payload)
                                Log.d("MqttService", "Message received: $t -> $payload")
                                onMessageReceived?.invoke(t, payload)
                            }
                        }
                    }
                    
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        // Not used
                    }
                })
            } catch (e: Exception) {
                Log.e("MqttService", "Error connecting", e)
                onConnectionStatusChanged?.invoke(false)
            }
        }
    }
    
    private fun subscribeToTopics() {
        try {
            mqttClient?.subscribe(topicData, 0)
            Log.d("MqttService", "Subscribed to $topicData")
            
            mqttClient?.subscribe(topicControl, 0)
            Log.d("MqttService", "Subscribed to $topicControl")
        } catch (e: Exception) {
            Log.e("MqttService", "Error subscribing", e)
        }
    }
    
    fun publish(topic: String, message: String) {
        scope.launch {
            try {
                val mqttMessage = MqttMessage(message.toByteArray())
                mqttMessage.qos = 0
                mqttClient?.publish(topic, mqttMessage)
                Log.d("MqttService", "Published to $topic")
            } catch (e: Exception) {
                Log.e("MqttService", "Error publishing", e)
            }
        }
    }
    
    fun disconnect() {
        scope.launch {
            try {
                mqttClient?.disconnect()
                onConnectionStatusChanged?.invoke(false)
            } catch (e: Exception) {
                Log.e("MqttService", "Error disconnecting", e)
            }
        }
    }
    
    fun isConnected(): Boolean {
        return mqttClient?.isConnected == true
    }
}
