package ecccomp.s2240788.mimamori.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import ecccomp.s2240788.mimamori.data.AppDatabase
import ecccomp.s2240788.mimamori.data.SensorData
import ecccomp.s2240788.mimamori.data.SensorDataDao
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application) {
    // ========== データベースの初期化 ==========
    // 1. データベースインスタンスを取得（シングルトン）
    private val database = AppDatabase.getDatabase(application)
    
    // 2. DAOを取得（データ操作のメソッドが使えるようになる）
    private val dao: SensorDataDao = database.sensorDataDao()
    
    // ========== UI状態の管理 ==========
    // Current sensor data
    private val _currentData = MutableLiveData<SensorData?>()
    val currentData: LiveData<SensorData?> = _currentData
    
    // Connection status
    private val _isConnected = MutableLiveData<Boolean>()
    val isConnected: LiveData<Boolean> = _isConnected
    
    // Fan state
    private val _fanState = MutableLiveData<Boolean>(false)
    val fanState: LiveData<Boolean> = _fanState
    
    // History data（FlowをLiveDataに変換）
    // Flow: データが変更されると自動的に通知される
    val allHistoryData: LiveData<List<SensorData>> = dao.getAllData().asLiveData()
    
    init {
        // アプリ起動時に最新データを読み込む
        loadLatestData()
    }
    
    fun updateConnectionStatus(connected: Boolean) {
        _isConnected.postValue(connected)  // postValue: バックグラウンドスレッドからも安全に呼び出せる
    }
    
    /**
     * MQTTメッセージを処理してデータベースに保存
     * 
     * 流れ:
     * 1. JSONを解析
     * 2. SensorDataオブジェクトを作成
     * 3. UI更新用にLiveDataに設定
     * 4. データベースに保存
     */
    fun processMqttMessage(topic: String, payload: String) {
        if (topic.contains("data")) {
            try {
                val json = JSONObject(payload)
                // JSONからSensorDataオブジェクトを作成
                val sensorData = SensorData(
                    status = json.optString("status", "ANZEN"),
                    temperature = json.optDouble("temp", 0.0).toFloat(),
                    humidity = json.optDouble("hum", 0.0).toFloat(),
                    discomfortIndex = json.optDouble("di", 0.0).toFloat(),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
                
                // UI更新用（即座に反映）
                _currentData.value = sensorData
                
                // データベースに保存（バックグラウンドで実行）
                saveData(sensorData)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (topic.contains("control")) {
            // Handle control messages if needed
        }
    }
    
    /**
     * データをデータベースに保存
     * 
     * viewModelScope.launch: 非同期実行（UIをブロックしない）
     * dao.insert(): データベースに追加
     */
    private fun saveData(data: SensorData) {
        viewModelScope.launch {
            dao.insert(data)  // ← ここでデータベースに保存される
        }
    }
    
    /**
     * 最新のデータを読み込む（アプリ起動時など）
     */
    private fun loadLatestData() {
        viewModelScope.launch {
            val latest = dao.getLatest()  // ← データベースから最新1件を取得
            _currentData.value = latest   // UIに反映
        }
    }
    
    /**
     * ステータスでフィルターした履歴データを取得
     * 例: getHistoryByStatus("KIKEN") → 危険状態のデータのみ
     */
    fun getHistoryByStatus(status: String): LiveData<List<SensorData>> {
        return dao.getDataByStatus(status).asLiveData()
    }
    
    /**
     * 全履歴データを削除
     */
    fun clearHistory() {
        viewModelScope.launch {
            dao.deleteAll()  // ← データベースから全データを削除
        }
    }
    
    fun setFanState(state: Boolean) {
        _fanState.value = state
    }
    
    fun getRecentData(limit: Int): LiveData<List<SensorData>> {
        return dao.getRecentData(limit).asLiveData()
    }
}
