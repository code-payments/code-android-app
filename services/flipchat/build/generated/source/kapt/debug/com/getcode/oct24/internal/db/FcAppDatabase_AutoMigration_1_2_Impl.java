package com.getcode.oct24.internal.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.lang.Override;
import java.lang.SuppressWarnings;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
final class FcAppDatabase_AutoMigration_1_2_Impl extends Migration {
  public FcAppDatabase_AutoMigration_1_2_Impl() {
    super(1, 2);
  }

  @Override
  public void migrate(@NonNull final SupportSQLiteDatabase db) {
    db.execSQL("CREATE TABLE IF NOT EXISTS `members` (`memberIdBase58` TEXT NOT NULL, `conversationIdBase58` TEXT NOT NULL, `memberName` TEXT, `imageUri` TEXT, PRIMARY KEY(`memberIdBase58`, `conversationIdBase58`))");
    db.execSQL("CREATE TABLE IF NOT EXISTS `_new_conversations` (`idBase58` TEXT NOT NULL, `title` TEXT NOT NULL, `imageUri` TEXT, `lastActivity` INTEGER, `isMuted` INTEGER NOT NULL, `unreadCount` INTEGER NOT NULL, PRIMARY KEY(`idBase58`))");
    db.execSQL("INSERT INTO `_new_conversations` (`idBase58`,`title`,`imageUri`,`lastActivity`,`isMuted`,`unreadCount`) SELECT `idBase58`,`title`,`imageUri`,`lastActivity`,`isMuted`,`unreadCount` FROM `conversations`");
    db.execSQL("DROP TABLE `conversations`");
    db.execSQL("ALTER TABLE `_new_conversations` RENAME TO `conversations`");
  }
}
