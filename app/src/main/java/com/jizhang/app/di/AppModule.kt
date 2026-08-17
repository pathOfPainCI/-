package com.jizhang.app.di

import android.content.Context
import com.jizhang.app.data.db.AppDatabase
import com.jizhang.app.data.db.BudgetDao
import com.jizhang.app.data.db.CategoryDao
import com.jizhang.app.data.db.RuleDao
import com.jizhang.app.data.db.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.build(context)

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideRuleDao(db: AppDatabase): RuleDao = db.ruleDao()

    @Provides
    fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()
}
