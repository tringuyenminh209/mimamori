package ecccomp.s2240788.mimamori.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity（エンティティ）: データベースのテーブル構造を定義
 * 
 * このクラスは、SQLiteデータベースの「sensor_data」テーブルを表します。
 * Roomは、このクラスを基に自動的にテーブルを作成します。
 * 
 * 例: 
 * ┌────┬────────┬──────────┬──────┬─────────┐
 * │ id │ status │temperature│humidity│timestamp│
 * ├────┼────────┼──────────┼──────┼─────────┤
 * │ 1  │ ANZEN  │  25.5    │ 65.0 │ 1234567 │
 * └────┴────────┴──────────┴──────┴─────────┘
 */
@Entity(tableName = "sensor_data")  // テーブル名を指定
data class SensorData(
    @PrimaryKey(autoGenerate = true)  // 主キー: 自動で1, 2, 3...と採番される
    val id: Long = 0,
    
    val status: String, // ANZEN, CHUI, KIKEN, SAMUI, REMOTE
    val temperature: Float,    // 温度（℃）
    val humidity: Float,       // 湿度（%）
    val discomfortIndex: Float, // 不快指数（DI）
    val timestamp: Long = System.currentTimeMillis()  // タイムスタンプ（ミリ秒）
)
