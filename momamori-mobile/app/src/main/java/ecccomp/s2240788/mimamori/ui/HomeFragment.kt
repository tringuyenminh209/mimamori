package ecccomp.s2240788.mimamori.ui

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import ecccomp.s2240788.mimamori.R
import ecccomp.s2240788.mimamori.data.SensorData
import ecccomp.s2240788.mimamori.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var prefs: SharedPreferences
    
    // Views
    private lateinit var dateTimeText: TextView
    private lateinit var seasonText: TextView
    private lateinit var deviceConnectionText: TextView
    private lateinit var lastReceivedText: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusBadge: TextView
    private lateinit var faceEmoji: TextView
    private lateinit var tempValue: TextView
    private lateinit var humValue: TextView
    private lateinit var diValue: TextView
    private lateinit var fanStatusText: TextView
    private lateinit var fanToggle: com.google.android.material.switchmaterial.SwitchMaterial
    private var updatingFanToggle = false
    private lateinit var tempChart: LineChart
    private lateinit var humChart: LineChart
    
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
        deviceConnectionText = view.findViewById(R.id.deviceConnectionText)
        lastReceivedText = view.findViewById(R.id.lastReceivedText)
        statusTitle = view.findViewById(R.id.statusTitle)
        statusBadge = view.findViewById(R.id.statusBadge)
        faceEmoji = view.findViewById(R.id.faceEmoji)
        tempValue = view.findViewById(R.id.tempValue)
        humValue = view.findViewById(R.id.humValue)
        diValue = view.findViewById(R.id.diValue)
        fanStatusText = view.findViewById(R.id.fanStatusText)
        fanToggle = view.findViewById(R.id.fanToggle)
        tempChart = view.findViewById(R.id.tempChart)
        humChart = view.findViewById(R.id.humChart)
        
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        
        // Initialize MQTT
        initMqtt()
        
        // Setup observers
        setupObservers()
        
        // Setup fan toggle
        fanToggle.setOnCheckedChangeListener { _, isChecked ->
            if (updatingFanToggle) return@setOnCheckedChangeListener
            viewModel.setFanState(isChecked)
            updateFanStatus(isChecked)
            sendFanControl(isChecked)
        }
        
        // Update date/time
        updateDateTime()

        setupCharts()
        
        return view
    }
    
    private fun initMqtt() {
        val broker = prefs.getString("mqtt_broker", "wss://broker.emqx.io:8084/mqtt") ?: "wss://broker.emqx.io:8084/mqtt"
        val topicData = prefs.getString("topic_data", "sk2a22/data") ?: "sk2a22/data"
        val topicControl = prefs.getString("topic_control", "sk2a22/control") ?: "sk2a22/control"

        (activity as? MainActivity)?.setConnectionStatusConnecting()
        viewModel.startMqtt(broker, topicData, topicControl)
    }
    
    private fun setupObservers() {
        viewModel.currentData.observe(viewLifecycleOwner, Observer { data ->
            data?.let { updateUI(it) }
        })
        
        viewModel.fanState.observe(viewLifecycleOwner, Observer { state ->
            updateFanStatus(state)
            if (fanToggle.isChecked != state) {
                updatingFanToggle = true
                fanToggle.isChecked = state
                updatingFanToggle = false
            }
        })

        viewModel.lastMessageAt.observe(viewLifecycleOwner, Observer {
            updateDeviceConnectionStatus()
        })

        viewModel.isConnected.observe(viewLifecycleOwner, Observer {
            updateDeviceConnectionStatus()
        })

        val chartPoints = prefs.getInt("chart_data_points", 20)
        viewModel.getRecentData(chartPoints).observe(viewLifecycleOwner, Observer { data ->
            updateCharts(data)
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
        fanStatusText.text = if (isOn) {
            getString(R.string.fan_on)
        } else {
            getString(R.string.fan_off)
        }
    }

    private fun setupCharts() {
        configureChart(tempChart)
        configureChart(humChart)
    }

    private fun configureChart(chart: LineChart) {
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setNoDataText("データなし")
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.axisRight.isEnabled = false
        chart.axisLeft.textColor = Color.GRAY
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.textColor = Color.GRAY
        chart.xAxis.setDrawGridLines(false)
        chart.xAxis.granularity = 1f
    }

    private fun updateCharts(data: List<SensorData>) {
        if (data.isEmpty()) {
            tempChart.clear()
            humChart.clear()
            tempChart.invalidate()
            humChart.invalidate()
            return
        }

        val sorted = data.sortedBy { it.timestamp }
        val tempEntries = sorted.mapIndexed { index, item ->
            Entry(index.toFloat(), item.temperature)
        }
        val humEntries = sorted.mapIndexed { index, item ->
            Entry(index.toFloat(), item.humidity)
        }

        val timeFormatter = object : ValueFormatter() {
            private val df = SimpleDateFormat("HH:mm", Locale.getDefault())
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt().coerceIn(0, sorted.lastIndex)
                return df.format(Date(sorted[idx].timestamp))
            }
        }

        tempChart.xAxis.valueFormatter = timeFormatter
        humChart.xAxis.valueFormatter = timeFormatter

        tempChart.data = LineData(buildDataSet(tempEntries, "Temp", Color.parseColor("#F44336")))
        humChart.data = LineData(buildDataSet(humEntries, "Hum", Color.parseColor("#2196F3")))
        tempChart.invalidate()
        humChart.invalidate()
    }

    private fun buildDataSet(entries: List<Entry>, label: String, color: Int): LineDataSet {
        return LineDataSet(entries, label).apply {
            this.color = color
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
    }
    
    private fun sendFanControl(isOn: Boolean) {
        viewModel.publishFanControl(isOn)
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

        updateDeviceConnectionStatus()
        
        // Update every second
        view?.postDelayed({
            updateDateTime()
        }, 1000)
    }

    private fun updateDeviceConnectionStatus() {
        val now = System.currentTimeMillis()
        val lastAt = viewModel.lastMessageAt.value ?: 0L
        val isBrokerConnected = viewModel.isConnected.value == true
        val timeoutMs = 6000L // M5Stack sends every ~2s

        val isDeviceConnected = isBrokerConnected && lastAt > 0 && now - lastAt <= timeoutMs
        deviceConnectionText.text = if (isDeviceConnected) {
            getString(R.string.device_connected)
        } else {
            getString(R.string.device_disconnected)
        }

        val timeText = if (lastAt > 0) {
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            timeFormat.format(Date(lastAt))
        } else {
            getString(R.string.device_last_received_none)
        }
        lastReceivedText.text = getString(R.string.device_last_received_format, timeText)
    }
    
    // MQTT connection is managed by ViewModel (shared across tabs)
}
