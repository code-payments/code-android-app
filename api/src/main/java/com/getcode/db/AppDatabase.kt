package com.getcode.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.getcode.model.*
import com.getcode.network.repository.decodeBase64
import com.getcode.vendor.Base58
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.subjects.BehaviorSubject
import net.sqlcipher.database.SupportFactory
import org.kin.sdk.base.tools.subByteArray
import java.io.File

@Database(
    entities = [
        CurrencyRate::class,
        FaqItem::class,
        PrefInt::class,
        PrefString::class,
        PrefBool::class,
        PrefDouble::class,
        SendLimit::class,
        GiftCard::class,
        ExchangeRate::class,
    ],
    autoMigrations = [
        AutoMigration(from = 7, to = 8, spec = AppDatabase.Migration7To8::class)
    ],
    version = 8
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun prefIntDao(): PrefIntDao
    abstract fun prefStringDao(): PrefStringDao
    abstract fun prefBoolDao(): PrefBoolDao
    abstract fun prefDoubleDao(): PrefDoubleDao
    abstract fun giftCardDao(): GiftCardDao
    abstract fun exchangeDao(): ExchangeDao

    @DeleteTable(tableName = "HistoricalTransaction")
    class Migration7To8 : AutoMigrationSpec
}

object Database {
    private const val dbNamePrefix = "AppDatabase"
    private const val dbNameSuffix = ".db"
    private var instance: AppDatabase? = null
    private var dbName: String = ""
    private var isInitSubject: BehaviorSubject<Boolean> = BehaviorSubject.create()
    var isInit = isInitSubject.toFlowable(BackpressureStrategy.DROP).filter { true }
    fun isOpen() = instance?.isOpen == true
    fun getInstance() = instance
    fun requireInstance() = instance!!

    fun init(context: Context, entropyB64: String) {
        instance?.close()
        val dbUniqueName = Base58.encode(entropyB64.toByteArray().subByteArray(0, 3))
        dbName = "$dbNamePrefix-$dbUniqueName$dbNameSuffix"

        instance =
            Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .openHelperFactory(SupportFactory(entropyB64.decodeBase64(), null, false))
                .fallbackToDestructiveMigration()
                .build()
        isInitSubject.onNext(true)
    }

    fun close() {
        instance?.close()
        instance = null
        isInitSubject.onNext(false)
    }

    fun delete(context: Context) {
        instance?.close()
        if (dbName.isEmpty()) return

        val databases = File(context.applicationInfo.dataDir + "/databases")
        val db = File(databases, dbName)
        db.delete()

        val journal = File(databases, "$dbName-journal")
        val shm = File(databases, "$dbName-shm")
        val wal = File(databases, "$dbName-wal")

        if (journal.exists()) journal.delete()
        if (shm.exists()) shm.delete()
        if (wal.exists()) shm.delete()
        isInitSubject.onNext(false)
    }
}