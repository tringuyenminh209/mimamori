package ecccomp.s2240788.mimamori.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import ecccomp.s2240788.mimamori.R
import ecccomp.s2240788.mimamori.viewmodel.MainViewModel
import androidx.preference.PreferenceManager

class SettingsFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var prefs: SharedPreferences
    
    // MQTT Settings
    private lateinit var mqttBrokerInput: TextInputEditText
    private lateinit var topicDataInput: TextInputEditText
    private lateinit var topicControlInput: TextInputEditText
    private lateinit var saveMqttBtn: MaterialButton
    
    // Alert Settings
    private lateinit var alertKikenSwitch: SwitchMaterial
    private lateinit var alertChuiSwitch: SwitchMaterial
    private lateinit var alertSamuiSwitch: SwitchMaterial
    
    // Chart Settings
    private lateinit var chartDataPointsInput: TextInputEditText
    private lateinit var updateIntervalInput: TextInputEditText
    
    // App Info
    private lateinit var versionText: android.widget.TextView
    private lateinit var lastUpdateText: android.widget.TextView
    
    // Actions
    private lateinit var clearHistoryBtn: MaterialButton
    private lateinit var resetSettingsBtn: MaterialButton
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        
        // Initialize views
        mqttBrokerInput = view.findViewById(R.id.mqttBrokerInput)
        topicDataInput = view.findViewById(R.id.topicDataInput)
        topicControlInput = view.findViewById(R.id.topicControlInput)
        saveMqttBtn = view.findViewById(R.id.saveMqttBtn)
        
        alertKikenSwitch = view.findViewById(R.id.alertKikenSwitch)
        alertChuiSwitch = view.findViewById(R.id.alertChuiSwitch)
        alertSamuiSwitch = view.findViewById(R.id.alertSamuiSwitch)
        
        chartDataPointsInput = view.findViewById(R.id.chartDataPointsInput)
        updateIntervalInput = view.findViewById(R.id.updateIntervalInput)
        
        versionText = view.findViewById(R.id.versionText)
        lastUpdateText = view.findViewById(R.id.lastUpdateText)
        
        clearHistoryBtn = view.findViewById(R.id.clearHistoryBtn)
        resetSettingsBtn = view.findViewById(R.id.resetSettingsBtn)
        
        loadSettings()
        setupListeners()
        
        return view
    }
    
    private fun loadSettings() {
        // MQTT Settings
        mqttBrokerInput.setText(prefs.getString("mqtt_broker", "wss://broker.emqx.io:8084/mqtt"))
        topicDataInput.setText(prefs.getString("topic_data", "sk2a22/data"))
        topicControlInput.setText(prefs.getString("topic_control", "sk2a22/control"))
        
        // Alert Settings
        alertKikenSwitch.isChecked = prefs.getBoolean("alert_kiken", true)
        alertChuiSwitch.isChecked = prefs.getBoolean("alert_chui", true)
        alertSamuiSwitch.isChecked = prefs.getBoolean("alert_samui", true)
        
        // Chart Settings
        chartDataPointsInput.setText(prefs.getInt("chart_data_points", 20).toString())
        updateIntervalInput.setText(prefs.getInt("update_interval", 2).toString())
        
        // App Info
        versionText.text = "1.0.0"
        val lastUpdate = prefs.getLong("last_update", 0)
        if (lastUpdate > 0) {
            val dateFormat = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
            lastUpdateText.text = dateFormat.format(java.util.Date(lastUpdate))
        } else {
            lastUpdateText.text = "--"
        }
    }
    
    private fun setupListeners() {
        saveMqttBtn.setOnClickListener {
            saveMqttSettings()
        }
        
        alertKikenSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("alert_kiken", isChecked).apply()
        }
        
        alertChuiSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("alert_chui", isChecked).apply()
        }
        
        alertSamuiSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("alert_samui", isChecked).apply()
        }
        
        clearHistoryBtn.setOnClickListener {
            viewModel.clearHistory()
            Toast.makeText(requireContext(), "履歴をクリアしました", Toast.LENGTH_SHORT).show()
        }
        
        resetSettingsBtn.setOnClickListener {
            resetSettings()
        }
    }
    
    private fun saveMqttSettings() {
        val broker = mqttBrokerInput.text?.toString() ?: ""
        val topicData = topicDataInput.text?.toString() ?: ""
        val topicControl = topicControlInput.text?.toString() ?: ""
        
        if (broker.isBlank() || topicData.isBlank() || topicControl.isBlank()) {
            Toast.makeText(requireContext(), "すべてのフィールドを入力してください", Toast.LENGTH_SHORT).show()
            return
        }
        
        prefs.edit()
            .putString("mqtt_broker", broker)
            .putString("topic_data", topicData)
            .putString("topic_control", topicControl)
            .apply()
        
        val chartDataPoints = chartDataPointsInput.text?.toString()?.toIntOrNull() ?: 20
        val updateInterval = updateIntervalInput.text?.toString()?.toIntOrNull() ?: 2
        
        prefs.edit()
            .putInt("chart_data_points", chartDataPoints)
            .putInt("update_interval", updateInterval)
            .putLong("last_update", System.currentTimeMillis())
            .apply()
        
        Toast.makeText(requireContext(), "設定を保存しました", Toast.LENGTH_SHORT).show()
        loadSettings()
    }
    
    private fun resetSettings() {
        prefs.edit().clear().apply()
        loadSettings()
        Toast.makeText(requireContext(), "設定をリセットしました", Toast.LENGTH_SHORT).show()
    }
}
