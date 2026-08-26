package com.jizhang.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        RuleEntity::class,
        BudgetEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun ruleDao(): RuleDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** 单例（小组件等非 Hilt 环境使用） */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "autobook.db")
                .addCallback(SEED_CALLBACK)
                .build()

        /** 首次建库时写入内置分类 + 高频规则种子 */
        private val SEED_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedCategories(db)
                seedRules(db)
            }
        }

        private fun seedCategories(db: SupportSQLiteDatabase) {
            // name, type, sortOrder
            val list = listOf(
                "餐饮" to "EXPENSE", "交通" to "EXPENSE", "购物" to "EXPENSE",
                "日用" to "EXPENSE", "娱乐" to "EXPENSE", "医疗" to "EXPENSE",
                "教育" to "EXPENSE", "居住" to "EXPENSE", "通讯" to "EXPENSE",
                "转账红包" to "NEUTRAL", "收入" to "INCOME", "其他" to "EXPENSE",
            )
            list.forEachIndexed { i, (name, type) ->
                db.execSQL(
                    "INSERT INTO categories (name, icon, type, sortOrder) VALUES ('$name', NULL, '$type', $i)"
                )
            }
        }

        private fun seedRules(db: SupportSQLiteDatabase) {
            // categoryName, pattern, priority
            val list = listOf(
                "餐饮" to "瑞幸", "餐饮" to "星巴克", "餐饮" to "麦当劳", "餐饮" to "肯德基",
                "餐饮" to "蜜雪冰城", "餐饮" to "美团外卖", "餐饮" to "饿了么",
                "交通" to "滴滴", "交通" to "地铁", "交通" to "公交", "交通" to "加油站",
                "购物" to "淘宝", "购物" to "京东", "购物" to "拼多多", "购物" to "天猫",
                "通讯" to "话费", "通讯" to "中国移动", "通讯" to "中国联通", "通讯" to "中国电信",
                "转账红包" to "转账", "转账红包" to "红包",
                "收入" to "工资",
            )
            list.forEachIndexed { i, (categoryName, pattern) ->
                db.execSQL(
                    "INSERT INTO rules (categoryId, categoryName, matchType, pattern, priority) " +
                        "VALUES (NULL, '$categoryName', 'CONTAINS', '$pattern', ${1000 - i})"
                )
            }
        }
    }
}
