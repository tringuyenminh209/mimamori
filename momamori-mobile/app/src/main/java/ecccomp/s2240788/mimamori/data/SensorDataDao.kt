package ecccomp.s2240788.mimamori.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object): データベースへの操作を定義
 * 
 * このインターフェースは、データベースに対する「読み取り」「書き込み」「削除」などの
 * 操作を定義します。Roomが自動的に実装を生成します。
 * 
 * 使用方法:
 * val dao = database.sensorDataDao()
 * dao.insert(data)  // データを追加
 * dao.getAllData()  // 全データを取得
 */
@Dao  // Data Access Objectのアノテーション
interface SensorDataDao {
    
    /**
     * 全データを取得（新しい順）
     * Flow: データが変更されると自動的に通知される
     */
    @Query("SELECT * FROM sensor_data ORDER BY timestamp DESC")
    fun getAllData(): Flow<List<SensorData>>
    
    /**
     * ステータスでフィルターして取得
     * 例: getDataByStatus("KIKEN") → 危険状態のデータのみ
     */
    @Query("SELECT * FROM sensor_data WHERE status = :status ORDER BY timestamp DESC")
    fun getDataByStatus(status: String): Flow<List<SensorData>>
    
    /**
     * 最新のN件を取得
     * 例: getRecentData(20) → 最新20件
     */
    @Query("SELECT * FROM sensor_data ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentData(limit: Int): Flow<List<SensorData>>
    
    /**
     * データを追加
     * suspend: 非同期処理（UIをブロックしない）
     * onConflict = REPLACE: 同じIDがあれば上書き
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: SensorData)
    
    /**
     * 全データを削除
     */
    @Query("DELETE FROM sensor_data")
    suspend fun deleteAll()
    
    /**
     * 最新の1件を取得
     * 例: アプリ起動時に最新の状態を表示
     */
    @Query("SELECT * FROM sensor_data ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): SensorData?
}
