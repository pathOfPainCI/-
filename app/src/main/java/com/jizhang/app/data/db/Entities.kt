package com.jizhang.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.jizhang.app.domain.model.RuleMatchType
import com.jizhang.app.domain.model.TransactionSource
import com.jizhang.app.domain.model.TransactionType

@Entity(tableName = "transactions", indices = [Index(value = ["dedupKey"], unique = true)])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,          // 分；解析失败时为 0 + needsReview
    val type: TransactionType,
    val merchant: String?,
    val note: String?,
    val categoryId: Long?,
    val source: TransactionSource,
    val transactionTime: Long,      // epoch ms
    val createdAt: Long,
    val dedupKey: String,           // 唯一索引
    val needsReview: Boolean,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String?,
    val type: TransactionType,
    val sortOrder: Int,
)

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long?,
    val categoryName: String,
    val matchType: RuleMatchType,
    val pattern: String,
    val priority: Int,
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long?,          // null = 总额预算
    val amountCents: Long,
    val month: String,              // "yyyy-MM"
)

class Converters {
    @TypeConverter fun typeToName(t: TransactionType): String = t.name
    @TypeConverter fun nameToType(name: String): TransactionType = TransactionType.valueOf(name)
    @TypeConverter fun sourceToName(s: TransactionSource): String = s.name
    @TypeConverter fun nameToSource(name: String): TransactionSource = TransactionSource.valueOf(name)
    @TypeConverter fun matchToName(m: RuleMatchType): String = m.name
    @TypeConverter fun nameToMatch(name: String): RuleMatchType = RuleMatchType.valueOf(name)
}
