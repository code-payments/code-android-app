package com.getcode.oct24.internal.db;

import androidx.annotation.NonNull;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.lang.Override;
import java.lang.SuppressWarnings;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
final class FcAppDatabase_AutoMigration_7_8_Impl extends Migration {
  private final AutoMigrationSpec callback = new FcAppDatabase.Migration7To8();

  public FcAppDatabase_AutoMigration_7_8_Impl() {
    super(7, 8);
  }

  @Override
  public void migrate(@NonNull final SupportSQLiteDatabase db) {
    db.execSQL("ALTER TABLE `conversations` ADD COLUMN `ownerIdBase58` TEXT DEFAULT NULL");
    callback.onPostMigrate(db);
  }
}
