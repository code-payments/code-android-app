package com.getcode.oct24.internal.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.lang.Override;
import java.lang.SuppressWarnings;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
final class FcAppDatabase_AutoMigration_2_3_Impl extends Migration {
  public FcAppDatabase_AutoMigration_2_3_Impl() {
    super(2, 3);
  }

  @Override
  public void migrate(@NonNull final SupportSQLiteDatabase db) {
    db.execSQL("ALTER TABLE `conversations` ADD COLUMN `roomNumber` INTEGER NOT NULL DEFAULT 0");
  }
}
