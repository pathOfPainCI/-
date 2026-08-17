package com.jizhang.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert suspend fun insert(t: TransactionEntity): Long

    @Insert suspend fun insertAll(list: List<TransactionEntity>)

    @Query("SELECT COUNT(*) FROM transactions WHERE dedupKey = :key")
    suspend fun countByDedupKey(key: String): Int

    @Query("SELECT * FROM transactions ORDER BY transactionTime DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE needsReview = 1 ORDER BY transactionTime DESC")
    fun observeNeedsReview(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transactionTime >= :start AND transactionTime < :end ORDER BY transactionTime DESC")
    fun observeByRange(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE type = :typeName AND transactionTime >= :start AND transactionTime < :end")
    suspend fun sumByType(typeName: String, start: Long, end: Long): Long

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
    suspend fun updateCategory(id: Long, categoryId: Long?)
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
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Insert suspend fun insert(b: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
