package com.getcode.oct24.internal.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class FcAppDatabase_Impl extends FcAppDatabase {
  private volatile PrefIntDao _prefIntDao;

  private volatile PrefStringDao _prefStringDao;

  private volatile PrefBoolDao _prefBoolDao;

  private volatile PrefDoubleDao _prefDoubleDao;

  private volatile ConversationDao _conversationDao;

  private volatile ConversationPointerDao _conversationPointerDao;

  private volatile ConversationMessageDao _conversationMessageDao;

  private volatile ConversationMemberDao _conversationMemberDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(8) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `PrefInt` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `PrefString` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `PrefBool` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `PrefDouble` (`key` TEXT NOT NULL, `value` REAL NOT NULL, PRIMARY KEY(`key`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `conversations` (`idBase58` TEXT NOT NULL, `ownerIdBase58` TEXT, `title` TEXT NOT NULL, `roomNumber` INTEGER NOT NULL DEFAULT 0, `imageUri` TEXT, `lastActivity` INTEGER, `isMuted` INTEGER NOT NULL, `unreadCount` INTEGER NOT NULL, PRIMARY KEY(`idBase58`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `members` (`memberIdBase58` TEXT NOT NULL, `conversationIdBase58` TEXT NOT NULL, `memberName` TEXT, `imageUri` TEXT, `isHost` INTEGER NOT NULL DEFAULT false, PRIMARY KEY(`memberIdBase58`, `conversationIdBase58`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `conversation_pointers` (`conversationIdBase58` TEXT NOT NULL, `messageIdString` TEXT NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`conversationIdBase58`, `status`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`idBase58` TEXT NOT NULL, `conversationIdBase58` TEXT NOT NULL, `senderIdBase58` TEXT NOT NULL DEFAULT '', `dateMillis` INTEGER NOT NULL, `deleted` INTEGER, PRIMARY KEY(`idBase58`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `message_contents` (`messageIdBase58` TEXT NOT NULL, `content` TEXT NOT NULL, PRIMARY KEY(`messageIdBase58`, `content`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'b519189cb8dcbb32702186e5d00a61d5')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `PrefInt`");
        db.execSQL("DROP TABLE IF EXISTS `PrefString`");
        db.execSQL("DROP TABLE IF EXISTS `PrefBool`");
        db.execSQL("DROP TABLE IF EXISTS `PrefDouble`");
        db.execSQL("DROP TABLE IF EXISTS `conversations`");
        db.execSQL("DROP TABLE IF EXISTS `members`");
        db.execSQL("DROP TABLE IF EXISTS `conversation_pointers`");
        db.execSQL("DROP TABLE IF EXISTS `messages`");
        db.execSQL("DROP TABLE IF EXISTS `message_contents`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsPrefInt = new HashMap<String, TableInfo.Column>(2);
        _columnsPrefInt.put("key", new TableInfo.Column("key", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPrefInt.put("value", new TableInfo.Column("value", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPrefInt = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPrefInt = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPrefInt = new TableInfo("PrefInt", _columnsPrefInt, _foreignKeysPrefInt, _indicesPrefInt);
        final TableInfo _existingPrefInt = TableInfo.read(db, "PrefInt");
        if (!_infoPrefInt.equals(_existingPrefInt)) {
          return new RoomOpenHelper.ValidationResult(false, "PrefInt(com.getcode.services.model.PrefInt).\n"
                  + " Expected:\n" + _infoPrefInt + "\n"
                  + " Found:\n" + _existingPrefInt);
        }
        final HashMap<String, TableInfo.Column> _columnsPrefString = new HashMap<String, TableInfo.Column>(2);
        _columnsPrefString.put("key", new TableInfo.Column("key", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPrefString.put("value", new TableInfo.Column("value", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPrefString = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPrefString = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPrefString = new TableInfo("PrefString", _columnsPrefString, _foreignKeysPrefString, _indicesPrefString);
        final TableInfo _existingPrefString = TableInfo.read(db, "PrefString");
        if (!_infoPrefString.equals(_existingPrefString)) {
          return new RoomOpenHelper.ValidationResult(false, "PrefString(com.getcode.services.model.PrefString).\n"
                  + " Expected:\n" + _infoPrefString + "\n"
                  + " Found:\n" + _existingPrefString);
        }
        final HashMap<String, TableInfo.Column> _columnsPrefBool = new HashMap<String, TableInfo.Column>(2);
        _columnsPrefBool.put("key", new TableInfo.Column("key", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPrefBool.put("value", new TableInfo.Column("value", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPrefBool = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPrefBool = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPrefBool = new TableInfo("PrefBool", _columnsPrefBool, _foreignKeysPrefBool, _indicesPrefBool);
        final TableInfo _existingPrefBool = TableInfo.read(db, "PrefBool");
        if (!_infoPrefBool.equals(_existingPrefBool)) {
          return new RoomOpenHelper.ValidationResult(false, "PrefBool(com.getcode.services.model.PrefBool).\n"
                  + " Expected:\n" + _infoPrefBool + "\n"
                  + " Found:\n" + _existingPrefBool);
        }
        final HashMap<String, TableInfo.Column> _columnsPrefDouble = new HashMap<String, TableInfo.Column>(2);
        _columnsPrefDouble.put("key", new TableInfo.Column("key", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPrefDouble.put("value", new TableInfo.Column("value", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPrefDouble = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPrefDouble = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPrefDouble = new TableInfo("PrefDouble", _columnsPrefDouble, _foreignKeysPrefDouble, _indicesPrefDouble);
        final TableInfo _existingPrefDouble = TableInfo.read(db, "PrefDouble");
        if (!_infoPrefDouble.equals(_existingPrefDouble)) {
          return new RoomOpenHelper.ValidationResult(false, "PrefDouble(com.getcode.services.model.PrefDouble).\n"
                  + " Expected:\n" + _infoPrefDouble + "\n"
                  + " Found:\n" + _existingPrefDouble);
        }
        final HashMap<String, TableInfo.Column> _columnsConversations = new HashMap<String, TableInfo.Column>(8);
        _columnsConversations.put("idBase58", new TableInfo.Column("idBase58", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConversations.put("ownerIdBase58", new TableInfo.Column("ownerIdBase58", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConversations.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConversations.put("roomNumber", new TableInfo.Column("roomNumber", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        _columnsConversations.put("imageUri", new TableInfo.Column("imageUri", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConversations.put("lastActivity", new TableInfo.Column("lastActivity", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConversations.put("isMuted", new TableInfo.Column("isMuted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConversations.put("unreadCount", new TableInfo.Column("unreadCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysConversations = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesConversations = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoConversations = new TableInfo("conversations", _columnsConversations, _foreignKeysConversations, _indicesConversations);
        final TableInfo _existingConversations = TableInfo.read(db, "conversations");
        if (!_infoConversations.equals(_existingConversations)) {
          return new RoomOpenHelper.ValidationResult(false, "conversations(com.getcode.oct24.domain.model.chat.Conversation).\n"
                  + " Expected:\n" + _infoConversations + "\n"
                  + " Found:\n" + _existingConversations);
        }
        final HashMap<String, TableInfo.Column> _columnsMembers = new HashMap<String, TableInfo.Column>(5);
        _columnsMembers.put("memberIdBase58", new TableInfo.Column("memberIdBase58", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMembers.put("conversationIdBase58", new TableInfo.Column("conversationIdBase58", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMembers.put("memberName", new TableInfo.Column("memberName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMembers.put("imageUri", new TableInfo.Column("imageUri", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMembers.put("isHost", new TableInfo.Column("isHost", "INTEGER", true, 0, "false", TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMembers = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMembers = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoMembers = new TableInfo("members", _columnsMembers, _foreignKeysMembers, _indicesMembers);
        final TableInfo _existingMembers = TableInfo.read(db, "members");
        if (!_infoMembers.equals(_existingMembers)) {
          return new RoomOpenHelper.ValidationResult(false, "members(com.getcode.oct24.domain.model.chat.ConversationMember).\n"
                  + " Expected:\n" + _infoMembers + "\n"
                  + " Found:\n" + _existingMembers);
        }
        final HashMap<String, TableInfo.Column> _columnsConversationPointers = new HashMap<String, TableInfo.Column>(3);
        _columnsConversationPointers.put("conversationIdBase58", new TableInfo.Column("conversationIdBase58", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConversationPointers.put("messageIdString", new TableInfo.Column("messageIdString", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConversationPointers.put("status", new TableInfo.Column("status", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysConversationPointers = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesConversationPointers = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoConversationPointers = new TableInfo("conversation_pointers", _columnsConversationPointers, _foreignKeysConversationPointers, _indicesConversationPointers);
        final TableInfo _existingConversationPointers = TableInfo.read(db, "conversation_pointers");
        if (!_infoConversationPointers.equals(_existingConversationPointers)) {
          return new RoomOpenHelper.ValidationResult(false, "conversation_pointers(com.getcode.oct24.domain.model.chat.ConversationPointerCrossRef).\n"
                  + " Expected:\n" + _infoConversationPointers + "\n"
                  + " Found:\n" + _existingConversationPointers);
        }
        final HashMap<String, TableInfo.Column> _columnsMessages = new HashMap<String, TableInfo.Column>(5);
        _columnsMessages.put("idBase58", new TableInfo.Column("idBase58", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMessages.put("conversationIdBase58", new TableInfo.Column("conversationIdBase58", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMessages.put("senderIdBase58", new TableInfo.Column("senderIdBase58", "TEXT", true, 0, "''", TableInfo.CREATED_FROM_ENTITY));
        _columnsMessages.put("dateMillis", new TableInfo.Column("dateMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMessages.put("deleted", new TableInfo.Column("deleted", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMessages = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMessages = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoMessages = new TableInfo("messages", _columnsMessages, _foreignKeysMessages, _indicesMessages);
        final TableInfo _existingMessages = TableInfo.read(db, "messages");
        if (!_infoMessages.equals(_existingMessages)) {
          return new RoomOpenHelper.ValidationResult(false, "messages(com.getcode.oct24.domain.model.chat.ConversationMessage).\n"
                  + " Expected:\n" + _infoMessages + "\n"
                  + " Found:\n" + _existingMessages);
        }
        final HashMap<String, TableInfo.Column> _columnsMessageContents = new HashMap<String, TableInfo.Column>(2);
        _columnsMessageContents.put("messageIdBase58", new TableInfo.Column("messageIdBase58", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMessageContents.put("content", new TableInfo.Column("content", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMessageContents = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMessageContents = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoMessageContents = new TableInfo("message_contents", _columnsMessageContents, _foreignKeysMessageContents, _indicesMessageContents);
        final TableInfo _existingMessageContents = TableInfo.read(db, "message_contents");
        if (!_infoMessageContents.equals(_existingMessageContents)) {
          return new RoomOpenHelper.ValidationResult(false, "message_contents(com.getcode.oct24.domain.model.chat.ConversationMessageContent).\n"
                  + " Expected:\n" + _infoMessageContents + "\n"
                  + " Found:\n" + _existingMessageContents);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "b519189cb8dcbb32702186e5d00a61d5", "a07342b2c19c3659db43408ddd110988");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "PrefInt","PrefString","PrefBool","PrefDouble","conversations","members","conversation_pointers","messages","message_contents");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `PrefInt`");
      _db.execSQL("DELETE FROM `PrefString`");
      _db.execSQL("DELETE FROM `PrefBool`");
      _db.execSQL("DELETE FROM `PrefDouble`");
      _db.execSQL("DELETE FROM `conversations`");
      _db.execSQL("DELETE FROM `members`");
      _db.execSQL("DELETE FROM `conversation_pointers`");
      _db.execSQL("DELETE FROM `messages`");
      _db.execSQL("DELETE FROM `message_contents`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(PrefIntDao.class, PrefIntDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(PrefStringDao.class, PrefStringDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(PrefBoolDao.class, PrefBoolDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(PrefDoubleDao.class, PrefDoubleDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ConversationDao.class, ConversationDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ConversationPointerDao.class, ConversationPointerDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ConversationMessageDao.class, ConversationMessageDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ConversationMemberDao.class, ConversationMemberDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    _autoMigrations.add(new FcAppDatabase_AutoMigration_1_2_Impl());
    _autoMigrations.add(new FcAppDatabase_AutoMigration_2_3_Impl());
    _autoMigrations.add(new FcAppDatabase_AutoMigration_3_4_Impl());
    _autoMigrations.add(new FcAppDatabase_AutoMigration_4_5_Impl());
    _autoMigrations.add(new FcAppDatabase_AutoMigration_5_6_Impl());
    _autoMigrations.add(new FcAppDatabase_AutoMigration_6_7_Impl());
    _autoMigrations.add(new FcAppDatabase_AutoMigration_7_8_Impl());
    return _autoMigrations;
  }

  @Override
  public PrefIntDao prefIntDao() {
    if (_prefIntDao != null) {
      return _prefIntDao;
    } else {
      synchronized(this) {
        if(_prefIntDao == null) {
          _prefIntDao = new PrefIntDao_Impl(this);
        }
        return _prefIntDao;
      }
    }
  }

  @Override
  public PrefStringDao prefStringDao() {
    if (_prefStringDao != null) {
      return _prefStringDao;
    } else {
      synchronized(this) {
        if(_prefStringDao == null) {
          _prefStringDao = new PrefStringDao_Impl(this);
        }
        return _prefStringDao;
      }
    }
  }

  @Override
  public PrefBoolDao prefBoolDao() {
    if (_prefBoolDao != null) {
      return _prefBoolDao;
    } else {
      synchronized(this) {
        if(_prefBoolDao == null) {
          _prefBoolDao = new PrefBoolDao_Impl(this);
        }
        return _prefBoolDao;
      }
    }
  }

  @Override
  public PrefDoubleDao prefDoubleDao() {
    if (_prefDoubleDao != null) {
      return _prefDoubleDao;
    } else {
      synchronized(this) {
        if(_prefDoubleDao == null) {
          _prefDoubleDao = new PrefDoubleDao_Impl(this);
        }
        return _prefDoubleDao;
      }
    }
  }

  @Override
  public ConversationDao conversationDao() {
    if (_conversationDao != null) {
      return _conversationDao;
    } else {
      synchronized(this) {
        if(_conversationDao == null) {
          _conversationDao = new ConversationDao_Impl(this);
        }
        return _conversationDao;
      }
    }
  }

  @Override
  public ConversationPointerDao conversationPointersDao() {
    if (_conversationPointerDao != null) {
      return _conversationPointerDao;
    } else {
      synchronized(this) {
        if(_conversationPointerDao == null) {
          _conversationPointerDao = new ConversationPointerDao_Impl(this);
        }
        return _conversationPointerDao;
      }
    }
  }

  @Override
  public ConversationMessageDao conversationMessageDao() {
    if (_conversationMessageDao != null) {
      return _conversationMessageDao;
    } else {
      synchronized(this) {
        if(_conversationMessageDao == null) {
          _conversationMessageDao = new ConversationMessageDao_Impl(this);
        }
        return _conversationMessageDao;
      }
    }
  }

  @Override
  public ConversationMemberDao conversationMembersDao() {
    if (_conversationMemberDao != null) {
      return _conversationMemberDao;
    } else {
      synchronized(this) {
        if(_conversationMemberDao == null) {
          _conversationMemberDao = new ConversationMemberDao_Impl(this);
        }
        return _conversationMemberDao;
      }
    }
  }
}
