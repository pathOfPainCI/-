package com.jizhang.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jizhang.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/** 按分类汇总结果（统计饼图） */
data class CategorySum(val categoryId: Long?, val total: Long)

@Dao
interface TransactionDao {
    @Insert suspend fun insert(t: TransactionEntity): Long

    @Insert suspend fun insertAll(list: List<TransactionEntity>)

    @Query("SELECT COUNT(*) FROM transactions WHERE dedupKey = :key")
    suspend fun countByDedupKey(key: String): Int

    @Query("SELECT * FROM transactions ORDER BY transactionTime DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY transactionTime DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE needsReview = 1 ORDER BY transactionTime DESC")
    fun observeNeedsReview(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transactionTime >= :start AND transactionTime < :end ORDER BY transactionTime DESC")
    fun observeByRange(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE merchant LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%' OR CAST(amountCents AS TEXT) LIKE '%' || :q || '%' ORDER BY transactionTime DESC")
    fun observeSearch(q: String): Flow<List<TransactionEntity>>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE type = :typeName AND transactionTime >= :start AND transactionTime < :end")
    suspend fun sumByType(typeName: String, start: Long, end: Long): Long

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
    suspend fun updateCategory(id: Long, categoryId: Long?)

    /** 找同一笔的通知记录（CSV 导入时合并用）：同来源类型、同金额、时间窗内 */
    @Query("SELECT * FROM transactions WHERE source = :notifSource AND type = :type AND amountCents = :amountCents AND transactionTime BETWEEN :startMs AND :endMs LIMIT 1")
    suspend fun findNotificationMatch(
        notifSource: String,
        type: TransactionType,
        amountCents: Long,
        startMs: Long,
        endMs: Long,
    ): TransactionEntity?

    /** 用 CSV 信息补全通知记录（金额/类型/商户/备注/分类），并标记为已核对 */
    @Query("UPDATE transactions SET amountCents = :amountCents, type = :type, merchant = :merchant, note = :note, categoryId = :categoryId, needsReview = 0 WHERE id = :id")
    suspend fun updateDetailFull(id: Long, amountCents: Long, type: TransactionType, merchant: String?, note: String?, categoryId: Long?)

    /** 找时间窗内最近的待核对通知记录（金额解析失败或方向无法判定），CSV 导入时补全 */
    @Query("SELECT * FROM transactions WHERE source = :notifSource AND needsReview = 1 AND transactionTime BETWEEN :startMs AND :endMs ORDER BY ABS(transactionTime - :midMs) LIMIT 1")
    suspend fun findNearestReviewMatch(notifSource: String, startMs: Long, endMs: Long, midMs: Long): TransactionEntity?

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 编辑/补录记录（待核对补全用） */
    @Query("UPDATE transactions SET amountCents = :amountCents, type = :type, merchant = :merchant, note = :note, categoryId = :categoryId, needsReview = 0 WHERE id = :id")
    suspend fun updateFull(id: Long, amountCents: Long, type: TransactionType, merchant: String?, note: String?, categoryId: Long?)

    /** 找账单已导入的同笔记录（通知延迟到达时反向去重） */
    @Query("SELECT * FROM transactions WHERE source = :csvSource AND amountCents = :amountCents AND transactionTime BETWEEN :startMs AND :endMs LIMIT 1")
    suspend fun findCsvMatch(csvSource: String, amountCents: Long, startMs: Long, endMs: Long): TransactionEntity?

    /** 某时间范围内按分类汇总支出（统计饼图用） */
    @Query("SELECT categoryId, SUM(amountCents) AS total FROM transactions WHERE type = 'EXPENSE' AND transactionTime >= :start AND transactionTime < :end GROUP BY categoryId ORDER BY total DESC")
    suspend fun expenseByCategory(start: Long, end: Long): List<CategorySum>

    /** 某分类在时间范围内的支出（分类预算用） */
    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE type = 'EXPENSE' AND categoryId = :categoryId AND transactionTime >= :start AND transactionTime < :end")
    suspend fun sumByCategory(categoryId: Long, start: Long, end: Long): Long
}

@Dao
interface CategoryDao {
    @Insert suspend fun insertAll(list: List<CategoryEntity>)

    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules")
    suspend fun getAll(): List<RuleEntity>

    @Query("SELECT * FROM rules ORDER BY priority DESC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Insert suspend fun insert(rule: RuleEntity)

    @Insert suspend fun insertAll(list: List<RuleEntity>)

    @Update suspend fun update(rule: RuleEntity)

    @Delete suspend fun delete(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Insert suspend fun insert(b: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 某月总额预算（categoryId IS NULL = 总额） */
    @Query("SELECT amountCents FROM budgets WHERE categoryId IS NULL AND month = :month LIMIT 1")
    suspend fun getTotalBudget(month: String): Long?

    @Query("DELETE FROM budgets WHERE categoryId IS NULL AND month = :month")
    suspend fun deleteTotalBudget(month: String)

    /** 某月的全部预算（总额 + 分类） */
    @Query("SELECT * FROM budgets WHERE month = :month")
    suspend fun getByMonth(month: String): List<BudgetEntity>

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId AND month = :month")
    suspend fun deleteCategoryBudget(categoryId: Long, month: String)
}
