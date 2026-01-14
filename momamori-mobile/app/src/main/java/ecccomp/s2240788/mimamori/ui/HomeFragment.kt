package ecccomp.s2240788.mimamori.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import ecccomp.s2240788.mimamori.R
import ecccomp.s2240788.mimamori.data.SensorData
import ecccomp.s2240788.mimamori.network.MqttService
import ecccomp.s2240788.mimamori.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var mqttService: MqttService
    private lateinit var prefs: SharedPreferences
    
    // Views
    private lateinit var dateTimeText: TextView
    private lateinit var seasonText: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusBadge: TextView
    private lateinit var faceEmoji: TextView
    private lateinit var tempValue: TextView
    private lateinit var humValue: TextView
    private lateinit var diValue: TextView
    private lateinit var fanStatusText: TextView
    private lateinit var fanToggle: com.google.android.material.switchmaterial.SwitchMaterial
    
    private val statusConfig = mapOf(
        "ANZEN" to StatusInfo("安全", "😊", R.color.status_anzen),
        "CHUI" to StatusInfo("注意", "😐", R.color.status_chui),
        "KIKEN" to StatusInfo("危険", "😰", R.color.status_kiken),
        "SAMUI" to StatusInfo("寒い", "🥶", R.color.status_samui),
        "REMOTE" to StatusInfo("リモート", "😎", R.color.status_remote)
    )
    
    data class StatusInfo(val badge: String, val emoji: String, val colorRes: Int)
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        
        // Initialize views
        dateTimeText = view.findViewById(R.id.dateTimeText)
        seasonText = view.findViewById(R.id.seasonText)
        statusTitle = view.findViewById(R.id.statusTitle)
        statusBadge = view.findViewById(R.id.statusBadge)
        faceEmoji = view.findViewById(R.id.faceEmoji)
        tempValue = view.findViewById(R.id.tempValue)
        humValue = view.findViewById(R.id.humValue)
        diValue = view.findViewById(R.id.diValue)
        fanStatusText = view.findViewById(R.id.fanStatusText)
        fanToggle = view.findViewById(R.id.fanToggle)
        
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        
        // Initialize MQTT
        initMqtt()
        
        // Setup observers
        setupObservers()
        
        // Setup fan toggle
        fanToggle.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setFanState(isChecked)
            updateFanStatus(isChecked)
            sendFanControl(isChecked)
        }
        
        // Update date/time
        updateDateTime()
        
        return view
    }
    
    private fun initMqtt() {
        mqttService = MqttService(requireContext())
        
        val broker = prefs.getString("mqtt_broker", "wss://broker.emqx.io:8084/mqtt") ?: "wss://broker.emqx.io:8084/mqtt"
        val topicData = prefs.getString("topic_data", "sk2a22/data") ?: "sk2a22/data"
        val topicControl = prefs.getString("topic_control", "sk2a22/control") ?: "sk2a22/control"
        
        mqttService.onMessageReceived = { topic, payload ->
            viewModel.processMqttMessage(topic, payload)
        }
        
        mqttService.onConnectionStatusChanged = { connected ->
            viewModel.updateConnectionStatus(connected)
            (activity as? MainActivity)?.updateConnectionStatus(connected)
        }
        
        (activity as? MainActivity)?.setConnectionStatusConnecting()
        mqttService.connect(broker, topicData, topicControl)
    }
    
    private fun setupObservers() {
        viewModel.currentData.observe(viewLifecycleOwner, Observer { data ->
            data?.let { updateUI(it) }
        })
        
        viewModel.fanState.observe(viewLifecycleOwner, Observer { state ->
            updateFanStatus(state)
            fanToggle.isChecked = state
        })
    }
    
    private fun updateUI(data: SensorData) {
        val status = statusConfig[data.status] ?: statusConfig["ANZEN"]!!
        
        statusTitle.text = data.status
        statusBadge.text = status.badge
        faceEmoji.text = status.emoji
        
        tempValue.text = String.format("%.1f", data.temperature)
        humValue.text = String.format("%.1f", data.humidity)
        diValue.text = String.format("%.1f", data.discomfortIndex)
        
        // Update status card background color
        val statusCard = view?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.statusCard)
        statusCard?.setCardBackgroundColor(resources.getColor(status.colorRes, null))
    }
    
    private fun updateFanStatus(isOn: Boolean) {
        fanStatusText.text = if (isOn) "ON" else "OFF"
    }
    
    private fun sendFanControl(isOn: Boolean) {
        val topicControl = prefs.getString("topic_control", "sk2a22/control") ?: "sk2a22/control"
        val message = if (isOn) "ON" else "OFF"
        mqttService.publish(topicControl, message)
    }
    
    private fun updateDateTime() {
        val now = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
        dateTimeText.text = dateFormat.format(now.time)
        
        val month = now.get(Calendar.MONTH) + 1
        val season = when {
            month == 12 || month <= 2 -> "冬"
            month >= 3 && month <= 5 -> "春"
            month >= 6 && month <= 8 -> "夏"
            else -> "秋"
        }
        seasonText.text = "[$season]"
        
        // Update every second
        view?.postDelayed({
            updateDateTime()
        }, 1000)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        mqttService.disconnect()
    }
}
