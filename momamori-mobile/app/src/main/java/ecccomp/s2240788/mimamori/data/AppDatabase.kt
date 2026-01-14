package ecccomp.s2240788.mimamori.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Database: データベース全体を管理するクラス
 * 
 * このクラスは:
 * 1. データベースの設定（どのEntityを使うか、バージョンなど）
 * 2. DAOの提供（sensorDataDao()）
 * 3. シングルトンパターン（1つのインスタンスのみ作成）
 * 
 * 使用方法:
 * val database = AppDatabase.getDatabase(context)
 * val dao = database.sensorDataDao()
 */
@Database(
    entities = [SensorData::class],  // 使用するEntityを指定
    version = 1,                      // データベースのバージョン（スキーマ変更時に増やす）
    exportSchema = false              // スキーマのエクスポートを無効化
)
abstract class AppDatabase : RoomDatabase() {
    /**
     * DAOを提供する抽象メソッド
     * Roomが自動的に実装を生成
     */
    abstract fun sensorDataDao(): SensorDataDao
    
    companion object {
        @Volatile  // マルチスレッド対応（volatile）
        private var INSTANCE: AppDatabase? = null
        
        /**
         * データベースインスタンスを取得（シングルトンパターン）
         * 
         * シングルトンとは:
         * - アプリ全体で1つのインスタンスのみ作成
         * - 2回目以降は既存のインスタンスを返す
         * 
         * なぜ必要？
         * - データベース接続は重い処理
         * - 複数のインスタンスがあると不整合が起きる
         * - メモリ効率が良い
         */
        fun getDatabase(context: Context): AppDatabase {
            // 既にインスタンスがあればそれを返す
            return INSTANCE ?: synchronized(this) {
                // なければ新規作成
                val instance = Room.databaseBuilder(
                    context.applicationContext,  // アプリケーションコンテキスト
                    AppDatabase::class.java,    // データベースクラス
                    "mimamori_database"         // データベースファイル名
                ).build()
                INSTANCE = instance  // インスタンスを保存
                instance
            }
        }
    }
}
