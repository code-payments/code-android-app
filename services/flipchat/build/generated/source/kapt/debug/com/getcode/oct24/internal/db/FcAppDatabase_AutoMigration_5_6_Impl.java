package com.getcode.oct24.internal.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.lang.Override;
import java.lang.SuppressWarnings;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
final class FcAppDatabase_AutoMigration_5_6_Impl extends Migration {
  public FcAppDatabase_AutoMigration_5_6_Impl() {
    super(5, 6);
  }

  @Override
  public void migrate(@NonNull final SupportSQLiteDatabase db) {
    db.execSQL("ALTER TABLE `members` ADD COLUMN `isHost` INTEGER NOT NULL DEFAULT false");
  }
}
