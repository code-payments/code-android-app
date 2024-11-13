package com.getcode.oct24.internal.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.paging.PagingSource;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.paging.LimitOffsetPagingSource;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.RelationUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.getcode.model.chat.MessageContent;
import com.getcode.model.chat.MessageStatus;
import com.getcode.oct24.domain.model.chat.Conversation;
import com.getcode.oct24.domain.model.chat.ConversationMember;
import com.getcode.oct24.domain.model.chat.ConversationMessage;
import com.getcode.oct24.domain.model.chat.ConversationMessageWithContent;
import com.getcode.oct24.domain.model.chat.ConversationPointerCrossRef;
import com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastMessage;
import com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers;
import com.getcode.services.db.SharedConverters;
import java.lang.Byte;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalArgumentException;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ConversationDao_Impl implements ConversationDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Conversation> __insertionAdapterOfConversation;

  private final EntityDeletionOrUpdateAdapter<Conversation> __deletionAdapterOfConversation;

  private final SharedSQLiteStatement __preparedStmtOfDeleteConversationById;

  private final SharedSQLiteStatement __preparedStmtOfClearConversations;

  private final SharedConverters __sharedConverters = new SharedConverters();

  public ConversationDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfConversation = new EntityInsertionAdapter<Conversation>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `conversations` (`idBase58`,`ownerIdBase58`,`title`,`roomNumber`,`imageUri`,`lastActivity`,`isMuted`,`unreadCount`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Conversation entity) {
        if (entity.getIdBase58() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getIdBase58());
        }
        if (entity.getOwnerIdBase58() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getOwnerIdBase58());
        }
        if (entity.getTitle() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getTitle());
        }
        statement.bindLong(4, entity.getRoomNumber());
        if (entity.getImageUri() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getImageUri());
        }
        if (entity.getLastActivity() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getLastActivity());
        }
        final int _tmp = entity.isMuted() ? 1 : 0;
        statement.bindLong(7, _tmp);
        statement.bindLong(8, entity.getUnreadCount());
      }
    };
    this.__deletionAdapterOfConversation = new EntityDeletionOrUpdateAdapter<Conversation>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `conversations` WHERE `idBase58` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Conversation entity) {
        if (entity.getIdBase58() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getIdBase58());
        }
      }
    };
    this.__preparedStmtOfDeleteConversationById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM conversations WHERE idBase58 = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearConversations = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM conversations";
        return _query;
      }
    };
  }

  @Override
  public Object upsertConversations(final Conversation[] conversation,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfConversation.insert(conversation);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public void deleteConversation(final Conversation conversation) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfConversation.handle(conversation);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public Object deleteConversationById(final String id,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteConversationById.acquire();
        int _argIndex = 1;
        if (id == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, id);
        }
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteConversationById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public void clearConversations() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfClearConversations.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfClearConversations.release(_stmt);
    }
  }

  @Override
  public PagingSource<Integer, ConversationWithMembersAndLastMessage> observeConversations() {
    final String _sql = "SELECT `idBase58`, `ownerIdBase58`, `title`, `roomNumber`, `imageUri`, `lastActivity`, `isMuted`, `unreadCount` FROM (\n"
            + "    SELECT * FROM conversations\n"
            + "    LEFT JOIN (\n"
            + "        SELECT conversationIdBase58, MAX(dateMillis) as lastMessageTimestamp \n"
            + "        FROM messages \n"
            + "        GROUP BY conversationIdBase58\n"
            + "    ) AS lastMessages ON conversations.idBase58 = lastMessages.conversationIdBase58\n"
            + "    WHERE roomNumber > 0\n"
            + "    ORDER BY lastMessageTimestamp DESC\n"
            + "    )";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return new LimitOffsetPagingSource<ConversationWithMembersAndLastMessage>(_statement, __db, "members", "message_contents", "messages", "conversations") {
      @Override
      @NonNull
      protected List<ConversationWithMembersAndLastMessage> convertRows(
          @NonNull final Cursor cursor) {
        final int _cursorIndexOfIdBase58 = 0;
        final int _cursorIndexOfOwnerIdBase58 = 1;
        final int _cursorIndexOfTitle = 2;
        final int _cursorIndexOfRoomNumber = 3;
        final int _cursorIndexOfImageUri = 4;
        final int _cursorIndexOfLastActivity = 5;
        final int _cursorIndexOfIsMuted = 6;
        final int _cursorIndexOfUnreadCount = 7;
        final ArrayMap<String, ArrayList<ConversationMember>> _collectionMembers = new ArrayMap<String, ArrayList<ConversationMember>>();
        final ArrayMap<String, ConversationMessageWithContent> _collectionLastMessage = new ArrayMap<String, ConversationMessageWithContent>();
        while (cursor.moveToNext()) {
          final String _tmpKey;
          if (cursor.isNull(_cursorIndexOfIdBase58)) {
            _tmpKey = null;
          } else {
            _tmpKey = cursor.getString(_cursorIndexOfIdBase58);
          }
          if (_tmpKey != null) {
            if (!_collectionMembers.containsKey(_tmpKey)) {
              _collectionMembers.put(_tmpKey, new ArrayList<ConversationMember>());
            }
          }
          final String _tmpKey_1;
          if (cursor.isNull(_cursorIndexOfIdBase58)) {
            _tmpKey_1 = null;
          } else {
            _tmpKey_1 = cursor.getString(_cursorIndexOfIdBase58);
          }
          if (_tmpKey_1 != null) {
            _collectionLastMessage.put(_tmpKey_1, null);
          }
        }
        cursor.moveToPosition(-1);
        __fetchRelationshipmembersAscomGetcodeOct24DomainModelChatConversationMember(_collectionMembers);
        __fetchRelationshipmessagesAscomGetcodeOct24DomainModelChatConversationMessageWithContent(_collectionLastMessage);
        final List<ConversationWithMembersAndLastMessage> _result = new ArrayList<ConversationWithMembersAndLastMessage>(cursor.getCount());
        while (cursor.moveToNext()) {
          final ConversationWithMembersAndLastMessage _item;
          final Conversation _tmpConversation;
          final String _tmpIdBase58;
          if (cursor.isNull(_cursorIndexOfIdBase58)) {
            _tmpIdBase58 = null;
          } else {
            _tmpIdBase58 = cursor.getString(_cursorIndexOfIdBase58);
          }
          final String _tmpOwnerIdBase58;
          if (cursor.isNull(_cursorIndexOfOwnerIdBase58)) {
            _tmpOwnerIdBase58 = null;
          } else {
            _tmpOwnerIdBase58 = cursor.getString(_cursorIndexOfOwnerIdBase58);
          }
          final String _tmpTitle;
          if (cursor.isNull(_cursorIndexOfTitle)) {
            _tmpTitle = null;
          } else {
            _tmpTitle = cursor.getString(_cursorIndexOfTitle);
          }
          final long _tmpRoomNumber;
          _tmpRoomNumber = cursor.getLong(_cursorIndexOfRoomNumber);
          final String _tmpImageUri;
          if (cursor.isNull(_cursorIndexOfImageUri)) {
            _tmpImageUri = null;
          } else {
            _tmpImageUri = cursor.getString(_cursorIndexOfImageUri);
          }
          final Long _tmpLastActivity;
          if (cursor.isNull(_cursorIndexOfLastActivity)) {
            _tmpLastActivity = null;
          } else {
            _tmpLastActivity = cursor.getLong(_cursorIndexOfLastActivity);
          }
          final boolean _tmpIsMuted;
          final int _tmp;
          _tmp = cursor.getInt(_cursorIndexOfIsMuted);
          _tmpIsMuted = _tmp != 0;
          final int _tmpUnreadCount;
          _tmpUnreadCount = cursor.getInt(_cursorIndexOfUnreadCount);
          _tmpConversation = new Conversation(_tmpIdBase58,_tmpOwnerIdBase58,_tmpTitle,_tmpRoomNumber,_tmpImageUri,_tmpLastActivity,_tmpIsMuted,_tmpUnreadCount);
          final ArrayList<ConversationMember> _tmpMembersCollection;
          final String _tmpKey_2;
          if (cursor.isNull(_cursorIndexOfIdBase58)) {
            _tmpKey_2 = null;
          } else {
            _tmpKey_2 = cursor.getString(_cursorIndexOfIdBase58);
          }
          if (_tmpKey_2 != null) {
            _tmpMembersCollection = _collectionMembers.get(_tmpKey_2);
          } else {
            _tmpMembersCollection = new ArrayList<ConversationMember>();
          }
          final ConversationMessageWithContent _tmpLastMessage;
          final String _tmpKey_3;
          if (cursor.isNull(_cursorIndexOfIdBase58)) {
            _tmpKey_3 = null;
          } else {
            _tmpKey_3 = cursor.getString(_cursorIndexOfIdBase58);
          }
          if (_tmpKey_3 != null) {
            _tmpLastMessage = _collectionLastMessage.get(_tmpKey_3);
          } else {
            _tmpLastMessage = null;
          }
          _item = new ConversationWithMembersAndLastMessage(_tmpConversation,_tmpMembersCollection,_tmpLastMessage);
          _result.add(_item);
        }
        return _result;
      }
    };
  }

  @Override
  public Flow<ConversationWithMembersAndLastPointers> observeConversation(final String id) {
    final String _sql = "\n"
            + "        SELECT * FROM conversations AS c\n"
            + "        LEFT JOIN members AS m ON c.idBase58 = m.conversationIdBase58\n"
            + "        LEFT JOIN conversation_pointers AS p ON c.idBase58 = p.conversationIdBase58\n"
            + "        WHERE c.idBase58 = ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (id == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, id);
    }
    return CoroutinesRoom.createFlow(__db, true, new String[] {"members", "conversation_pointers",
        "conversations"}, new Callable<ConversationWithMembersAndLastPointers>() {
      @Override
      @Nullable
      public ConversationWithMembersAndLastPointers call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfIdBase58 = CursorUtil.getColumnIndexOrThrow(_cursor, "idBase58");
            final int _cursorIndexOfOwnerIdBase58 = CursorUtil.getColumnIndexOrThrow(_cursor, "ownerIdBase58");
            final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
            final int _cursorIndexOfRoomNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "roomNumber");
            final int _cursorIndexOfImageUri = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUri");
            final int _cursorIndexOfLastActivity = CursorUtil.getColumnIndexOrThrow(_cursor, "lastActivity");
            final int _cursorIndexOfIsMuted = CursorUtil.getColumnIndexOrThrow(_cursor, "isMuted");
            final int _cursorIndexOfUnreadCount = CursorUtil.getColumnIndexOrThrow(_cursor, "unreadCount");
            final ArrayMap<String, ArrayList<ConversationMember>> _collectionMembers = new ArrayMap<String, ArrayList<ConversationMember>>();
            final ArrayMap<String, ArrayList<ConversationPointerCrossRef>> _collectionPointersCrossRef = new ArrayMap<String, ArrayList<ConversationPointerCrossRef>>();
            while (_cursor.moveToNext()) {
              final String _tmpKey;
              if (_cursor.isNull(_cursorIndexOfIdBase58)) {
                _tmpKey = null;
              } else {
                _tmpKey = _cursor.getString(_cursorIndexOfIdBase58);
              }
              if (_tmpKey != null) {
                if (!_collectionMembers.containsKey(_tmpKey)) {
                  _collectionMembers.put(_tmpKey, new ArrayList<ConversationMember>());
                }
              }
              final String _tmpKey_1;
              if (_cursor.isNull(_cursorIndexOfIdBase58)) {
                _tmpKey_1 = null;
              } else {
                _tmpKey_1 = _cursor.getString(_cursorIndexOfIdBase58);
              }
              if (_tmpKey_1 != null) {
                if (!_collectionPointersCrossRef.containsKey(_tmpKey_1)) {
                  _collectionPointersCrossRef.put(_tmpKey_1, new ArrayList<ConversationPointerCrossRef>());
                }
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshipmembersAscomGetcodeOct24DomainModelChatConversationMember(_collectionMembers);
            __fetchRelationshipconversationPointersAscomGetcodeOct24DomainModelChatConversationPointerCrossRef(_collectionPointersCrossRef);
            final ConversationWithMembersAndLastPointers _result;
            if (_cursor.moveToFirst()) {
              final Conversation _tmpConversation;
              final String _tmpIdBase58;
              if (_cursor.isNull(_cursorIndexOfIdBase58)) {
                _tmpIdBase58 = null;
              } else {
                _tmpIdBase58 = _cursor.getString(_cursorIndexOfIdBase58);
              }
              final String _tmpOwnerIdBase58;
              if (_cursor.isNull(_cursorIndexOfOwnerIdBase58)) {
                _tmpOwnerIdBase58 = null;
              } else {
                _tmpOwnerIdBase58 = _cursor.getString(_cursorIndexOfOwnerIdBase58);
              }
              final String _tmpTitle;
              if (_cursor.isNull(_cursorIndexOfTitle)) {
                _tmpTitle = null;
              } else {
                _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
              }
              final long _tmpRoomNumber;
              _tmpRoomNumber = _cursor.getLong(_cursorIndexOfRoomNumber);
              final String _tmpImageUri;
              if (_cursor.isNull(_cursorIndexOfImageUri)) {
                _tmpImageUri = null;
              } else {
                _tmpImageUri = _cursor.getString(_cursorIndexOfImageUri);
              }
              final Long _tmpLastActivity;
              if (_cursor.isNull(_cursorIndexOfLastActivity)) {
                _tmpLastActivity = null;
              } else {
                _tmpLastActivity = _cursor.getLong(_cursorIndexOfLastActivity);
              }
              final boolean _tmpIsMuted;
              final int _tmp;
              _tmp = _cursor.getInt(_cursorIndexOfIsMuted);
              _tmpIsMuted = _tmp != 0;
              final int _tmpUnreadCount;
              _tmpUnreadCount = _cursor.getInt(_cursorIndexOfUnreadCount);
              _tmpConversation = new Conversation(_tmpIdBase58,_tmpOwnerIdBase58,_tmpTitle,_tmpRoomNumber,_tmpImageUri,_tmpLastActivity,_tmpIsMuted,_tmpUnreadCount);
              final ArrayList<ConversationMember> _tmpMembersCollection;
              final String _tmpKey_2;
              if (_cursor.isNull(_cursorIndexOfIdBase58)) {
                _tmpKey_2 = null;
              } else {
                _tmpKey_2 = _cursor.getString(_cursorIndexOfIdBase58);
              }
              if (_tmpKey_2 != null) {
                _tmpMembersCollection = _collectionMembers.get(_tmpKey_2);
              } else {
                _tmpMembersCollection = new ArrayList<ConversationMember>();
              }
              final ArrayList<ConversationPointerCrossRef> _tmpPointersCrossRefCollection;
              final String _tmpKey_3;
              if (_cursor.isNull(_cursorIndexOfIdBase58)) {
                _tmpKey_3 = null;
              } else {
                _tmpKey_3 = _cursor.getString(_cursorIndexOfIdBase58);
              }
              if (_tmpKey_3 != null) {
                _tmpPointersCrossRefCollection = _collectionPointersCrossRef.get(_tmpKey_3);
              } else {
                _tmpPointersCrossRefCollection = new ArrayList<ConversationPointerCrossRef>();
              }
              _result = new ConversationWithMembersAndLastPointers(_tmpConversation,_tmpMembersCollection,_tmpPointersCrossRefCollection);
            } else {
              _result = null;
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
          }
        } finally {
          __db.endTransaction();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object findConversation(final String id,
      final Continuation<? super ConversationWithMembersAndLastPointers> $completion) {
    final String _sql = "SELECT * FROM conversations WHERE idBase58 = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (id == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, id);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ConversationWithMembersAndLastPointers>() {
      @Override
      @Nullable
      public ConversationWithMembersAndLastPointers call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
        try {
          final int _cursorIndexOfIdBase58 = CursorUtil.getColumnIndexOrThrow(_cursor, "idBase58");
          final int _cursorIndexOfOwnerIdBase58 = CursorUtil.getColumnIndexOrThrow(_cursor, "ownerIdBase58");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfRoomNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "roomNumber");
          final int _cursorIndexOfImageUri = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUri");
          final int _cursorIndexOfLastActivity = CursorUtil.getColumnIndexOrThrow(_cursor, "lastActivity");
          final int _cursorIndexOfIsMuted = CursorUtil.getColumnIndexOrThrow(_cursor, "isMuted");
          final int _cursorIndexOfUnreadCount = CursorUtil.getColumnIndexOrThrow(_cursor, "unreadCount");
          final ArrayMap<String, ArrayList<ConversationMember>> _collectionMembers = new ArrayMap<String, ArrayList<ConversationMember>>();
          final ArrayMap<String, ArrayList<ConversationPointerCrossRef>> _collectionPointersCrossRef = new ArrayMap<String, ArrayList<ConversationPointerCrossRef>>();
          while (_cursor.moveToNext()) {
            final String _tmpKey;
            if (_cursor.isNull(_cursorIndexOfIdBase58)) {
              _tmpKey = null;
            } else {
              _tmpKey = _cursor.getString(_cursorIndexOfIdBase58);
            }
            if (_tmpKey != null) {
              if (!_collectionMembers.containsKey(_tmpKey)) {
                _collectionMembers.put(_tmpKey, new ArrayList<ConversationMember>());
              }
            }
            final String _tmpKey_1;
            if (_cursor.isNull(_cursorIndexOfIdBase58)) {
              _tmpKey_1 = null;
            } else {
              _tmpKey_1 = _cursor.getString(_cursorIndexOfIdBase58);
            }
            if (_tmpKey_1 != null) {
              if (!_collectionPointersCrossRef.containsKey(_tmpKey_1)) {
                _collectionPointersCrossRef.put(_tmpKey_1, new ArrayList<ConversationPointerCrossRef>());
              }
            }
          }
          _cursor.moveToPosition(-1);
          __fetchRelationshipmembersAscomGetcodeOct24DomainModelChatConversationMember(_collectionMembers);
          __fetchRelationshipconversationPointersAscomGetcodeOct24DomainModelChatConversationPointerCrossRef(_collectionPointersCrossRef);
          final ConversationWithMembersAndLastPointers _result;
          if (_cursor.moveToFirst()) {
            final Conversation _tmpConversation;
            final String _tmpIdBase58;
            if (_cursor.isNull(_cursorIndexOfIdBase58)) {
              _tmpIdBase58 = null;
            } else {
              _tmpIdBase58 = _cursor.getString(_cursorIndexOfIdBase58);
            }
            final String _tmpOwnerIdBase58;
            if (_cursor.isNull(_cursorIndexOfOwnerIdBase58)) {
              _tmpOwnerIdBase58 = null;
            } else {
              _tmpOwnerIdBase58 = _cursor.getString(_cursorIndexOfOwnerIdBase58);
            }
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            final long _tmpRoomNumber;
            _tmpRoomNumber = _cursor.getLong(_cursorIndexOfRoomNumber);
            final String _tmpImageUri;
            if (_cursor.isNull(_cursorIndexOfImageUri)) {
              _tmpImageUri = null;
            } else {
              _tmpImageUri = _cursor.getString(_cursorIndexOfImageUri);
            }
            final Long _tmpLastActivity;
            if (_cursor.isNull(_cursorIndexOfLastActivity)) {
              _tmpLastActivity = null;
            } else {
              _tmpLastActivity = _cursor.getLong(_cursorIndexOfLastActivity);
            }
            final boolean _tmpIsMuted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsMuted);
            _tmpIsMuted = _tmp != 0;
            final int _tmpUnreadCount;
            _tmpUnreadCount = _cursor.getInt(_cursorIndexOfUnreadCount);
            _tmpConversation = new Conversation(_tmpIdBase58,_tmpOwnerIdBase58,_tmpTitle,_tmpRoomNumber,_tmpImageUri,_tmpLastActivity,_tmpIsMuted,_tmpUnreadCount);
            final ArrayList<ConversationMember> _tmpMembersCollection;
            final String _tmpKey_2;
            if (_cursor.isNull(_cursorIndexOfIdBase58)) {
              _tmpKey_2 = null;
            } else {
              _tmpKey_2 = _cursor.getString(_cursorIndexOfIdBase58);
            }
            if (_tmpKey_2 != null) {
              _tmpMembersCollection = _collectionMembers.get(_tmpKey_2);
            } else {
              _tmpMembersCollection = new ArrayList<ConversationMember>();
            }
            final ArrayList<ConversationPointerCrossRef> _tmpPointersCrossRefCollection;
            final String _tmpKey_3;
            if (_cursor.isNull(_cursorIndexOfIdBase58)) {
              _tmpKey_3 = null;
            } else {
              _tmpKey_3 = _cursor.getString(_cursorIndexOfIdBase58);
            }
            if (_tmpKey_3 != null) {
              _tmpPointersCrossRefCollection = _collectionPointersCrossRef.get(_tmpKey_3);
            } else {
              _tmpPointersCrossRefCollection = new ArrayList<ConversationPointerCrossRef>();
            }
            _result = new ConversationWithMembersAndLastPointers(_tmpConversation,_tmpMembersCollection,_tmpPointersCrossRefCollection);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object queryConversations(final Continuation<? super List<Conversation>> $completion) {
    final String _sql = "SELECT * FROM conversations";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Conversation>>() {
      @Override
      @NonNull
      public List<Conversation> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfIdBase58 = CursorUtil.getColumnIndexOrThrow(_cursor, "idBase58");
          final int _cursorIndexOfOwnerIdBase58 = CursorUtil.getColumnIndexOrThrow(_cursor, "ownerIdBase58");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfRoomNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "roomNumber");
          final int _cursorIndexOfImageUri = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUri");
          final int _cursorIndexOfLastActivity = CursorUtil.getColumnIndexOrThrow(_cursor, "lastActivity");
          final int _cursorIndexOfIsMuted = CursorUtil.getColumnIndexOrThrow(_cursor, "isMuted");
          final int _cursorIndexOfUnreadCount = CursorUtil.getColumnIndexOrThrow(_cursor, "unreadCount");
          final List<Conversation> _result = new ArrayList<Conversation>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Conversation _item;
            final String _tmpIdBase58;
            if (_cursor.isNull(_cursorIndexOfIdBase58)) {
              _tmpIdBase58 = null;
            } else {
              _tmpIdBase58 = _cursor.getString(_cursorIndexOfIdBase58);
            }
            final String _tmpOwnerIdBase58;
            if (_cursor.isNull(_cursorIndexOfOwnerIdBase58)) {
              _tmpOwnerIdBase58 = null;
            } else {
              _tmpOwnerIdBase58 = _cursor.getString(_cursorIndexOfOwnerIdBase58);
            }
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            final long _tmpRoomNumber;
            _tmpRoomNumber = _cursor.getLong(_cursorIndexOfRoomNumber);
            final String _tmpImageUri;
            if (_cursor.isNull(_cursorIndexOfImageUri)) {
              _tmpImageUri = null;
            } else {
              _tmpImageUri = _cursor.getString(_cursorIndexOfImageUri);
            }
            final Long _tmpLastActivity;
            if (_cursor.isNull(_cursorIndexOfLastActivity)) {
              _tmpLastActivity = null;
            } else {
              _tmpLastActivity = _cursor.getLong(_cursorIndexOfLastActivity);
            }
            final boolean _tmpIsMuted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsMuted);
            _tmpIsMuted = _tmp != 0;
            final int _tmpUnreadCount;
            _tmpUnreadCount = _cursor.getInt(_cursorIndexOfUnreadCount);
            _item = new Conversation(_tmpIdBase58,_tmpOwnerIdBase58,_tmpTitle,_tmpRoomNumber,_tmpImageUri,_tmpLastActivity,_tmpIsMuted,_tmpUnreadCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getConversationWithMembersAndLastMessage(final String conversationId,
      final Continuation<? super ConversationWithMembersAndLastMessage> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM conversations \n"
            + "        WHERE idBase58 = ? \n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (conversationId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, conversationId);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, true, _cancellationSignal, new Callable<ConversationWithMembersAndLastMessage>() {
      @Override
      @Nullable
      public ConversationWithMembersAndLastMessage call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfIdBase58 = CursorUtil.getColumnIndexOrThrow(_cursor, "idBase58");
            final int _cursorIndexOfOwnerIdBase58 = CursorUtil.getColumnIndexOrThrow(_cursor, "ownerIdBase58");
            final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
            final int _cursorIndexOfRoomNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "roomNumber");
            final int _cursorIndexOfImageUri = CursorUtil.getColumnIndexOrThrow(_cursor, "imageUri");
            final int _cursorIndexOfLastActivity = CursorUtil.getColumnIndexOrThrow(_cursor, "lastActivity");
            final int _cursorIndexOfIsMuted = CursorUtil.getColumnIndexOrThrow(_cursor, "isMuted");
            final int _cursorIndexOfUnreadCount = CursorUtil.getColumnIndexOrThrow(_cursor, "unreadCount");
            final ArrayMap<String, ArrayList<ConversationMember>> _collectionMembers = new ArrayMap<String, ArrayList<ConversationMember>>();
            final ArrayMap<String, ConversationMessageWithContent> _collectionLastMessage = new ArrayMap<String, ConversationMessageWithContent>();
            while (_cursor.moveToNext()) {
              final String _tmpKey;
              if (_cursor.isNull(_cursorIndexOfIdBase58)) {
                _tmpKey = null;
              } else {
                _tmpKey = _cursor.getString(_cursorIndexOfIdBase58);
              }
              if (_tmpKey != null) {
                if (!_collectionMembers.containsKey(_tmpKey)) {
                  _collectionMembers.put(_tmpKey, new ArrayList<ConversationMember>());
                }
              }
              final String _tmpKey_1;
              if (_cursor.isNull(_cursorIndexOfIdBase58)) {
                _tmpKey_1 = null;
              } else {
                _tmpKey_1 = _cursor.getString(_cursorIndexOfIdBase58);
              }
              if (_tmpKey_1 != null) {
                _collectionLastMessage.put(_tmpKey_1, null);
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshipmembersAscomGetcodeOct24DomainModelChatConversationMember(_collectionMembers);
            __fetchRelationshipmessagesAscomGetcodeOct24DomainModelChatConversationMessageWithContent(_collectionLastMessage);
            final ConversationWithMembersAndLastMessage _result;
            if (_cursor.moveToFirst()) {
              final Conversation _tmpConversation;
              final String _tmpIdBase58;
              if (_cursor.isNull(_cursorIndexOfIdBase58)) {
                _tmpIdBase58 = null;
              } else {
                _tmpIdBase58 = _cursor.getString(_cursorIndexOfIdBase58);
              }
              final String _tmpOwnerIdBase58;
              if (_cursor.isNull(_cursorIndexOfOwnerIdBase58)) {
                _tmpOwnerIdBase58 = null;
              } else {
                _tmpOwnerIdBase58 = _cursor.getString(_cursorIndexOfOwnerIdBase58);
              }
              final String _tmpTitle;
              if (_cursor.isNull(_cursorIndexOfTitle)) {
                _tmpTitle = null;
              } else {
                _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
              }
              final long _tmpRoomNumber;
              _tmpRoomNumber = _cursor.getLong(_cursorIndexOfRoomNumber);
              final String _tmpImageUri;
              if (_cursor.isNull(_cursorIndexOfImageUri)) {
                _tmpImageUri = null;
              } else {
                _tmpImageUri = _cursor.getString(_cursorIndexOfImageUri);
              }
              final Long _tmpLastActivity;
              if (_cursor.isNull(_cursorIndexOfLastActivity)) {
                _tmpLastActivity = null;
              } else {
                _tmpLastActivity = _cursor.getLong(_cursorIndexOfLastActivity);
              }
              final boolean _tmpIsMuted;
              final int _tmp;
              _tmp = _cursor.getInt(_cursorIndexOfIsMuted);
              _tmpIsMuted = _tmp != 0;
              final int _tmpUnreadCount;
              _tmpUnreadCount = _cursor.getInt(_cursorIndexOfUnreadCount);
              _tmpConversation = new Conversation(_tmpIdBase58,_tmpOwnerIdBase58,_tmpTitle,_tmpRoomNumber,_tmpImageUri,_tmpLastActivity,_tmpIsMuted,_tmpUnreadCount);
              final ArrayList<ConversationMember> _tmpMembersCollection;
              final String _tmpKey_2;
              if (_cursor.isNull(_cursorIndexOfIdBase58)) {
                _tmpKey_2 = null;
              } else {
                _tmpKey_2 = _cursor.getString(_cursorIndexOfIdBase58);
              }
              if (_tmpKey_2 != null) {
                _tmpMembersCollection = _collectionMembers.get(_tmpKey_2);
              } else {
                _tmpMembersCollection = new ArrayList<ConversationMember>();
              }
              final ConversationMessageWithContent _tmpLastMessage;
              final String _tmpKey_3;
              if (_cursor.isNull(_cursorIndexOfIdBase58)) {
                _tmpKey_3 = null;
              } else {
                _tmpKey_3 = _cursor.getString(_cursorIndexOfIdBase58);
              }
              if (_tmpKey_3 != null) {
                _tmpLastMessage = _collectionLastMessage.get(_tmpKey_3);
              } else {
                _tmpLastMessage = null;
              }
              _result = new ConversationWithMembersAndLastMessage(_tmpConversation,_tmpMembersCollection,_tmpLastMessage);
            } else {
              _result = null;
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
            _statement.release();
          }
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object purgeConversationsNotInByString(final List<String> chatIds,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("DELETE FROM conversations WHERE idBase58 NOT IN (");
        final int _inputSize = chatIds.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (String _item : chatIds) {
          if (_item == null) {
            _stmt.bindNull(_argIndex);
          } else {
            _stmt.bindString(_argIndex, _item);
          }
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<ConversationWithMembersAndLastPointers> observeConversation(final List<Byte> id) {
    return ConversationDao.DefaultImpls.observeConversation(ConversationDao_Impl.this, id);
  }

  @Override
  public Object findConversation(final List<Byte> id,
      final Continuation<? super ConversationWithMembersAndLastPointers> $completion) {
    return ConversationDao.DefaultImpls.findConversation(ConversationDao_Impl.this, id, $completion);
  }

  @Override
  public Object deleteConversationById(final List<Byte> id,
      final Continuation<? super Unit> $completion) {
    return ConversationDao.DefaultImpls.deleteConversationById(ConversationDao_Impl.this, id, $completion);
  }

  @Override
  public Object purgeConversationsNotIn(final List<? extends List<Byte>> chatIds,
      final Continuation<? super Unit> $completion) {
    return ConversationDao.DefaultImpls.purgeConversationsNotIn(ConversationDao_Impl.this, chatIds, $completion);
  }

  @Override
  public Object resetUnreadCount(final String conversationId,
      final Continuation<? super Unit> $completion) {
    return ConversationDao.DefaultImpls.resetUnreadCount(ConversationDao_Impl.this, conversationId, $completion);
  }

  @Override
  public Object resetUnreadCount(final List<Byte> conversationId,
      final Continuation<? super Unit> $completion) {
    return ConversationDao.DefaultImpls.resetUnreadCount(ConversationDao_Impl.this, conversationId, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }

  private void __fetchRelationshipmembersAscomGetcodeOct24DomainModelChatConversationMember(
      @NonNull final ArrayMap<String, ArrayList<ConversationMember>> _map) {
    final Set<String> __mapKeySet = _map.keySet();
    if (__mapKeySet.isEmpty()) {
      return;
    }
    if (_map.size() > RoomDatabase.MAX_BIND_PARAMETER_CNT) {
      RelationUtil.recursiveFetchArrayMap(_map, true, (map) -> {
        __fetchRelationshipmembersAscomGetcodeOct24DomainModelChatConversationMember(map);
        return Unit.INSTANCE;
      });
      return;
    }
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT `memberIdBase58`,`conversationIdBase58`,`memberName`,`imageUri`,`isHost` FROM `members` WHERE `conversationIdBase58` IN (");
    final int _inputSize = __mapKeySet == null ? 1 : __mapKeySet.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _stmt = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    if (__mapKeySet == null) {
      _stmt.bindNull(_argIndex);
    } else {
      for (String _item : __mapKeySet) {
        if (_item == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, _item);
        }
        _argIndex++;
      }
    }
    final Cursor _cursor = DBUtil.query(__db, _stmt, false, null);
    try {
      final int _itemKeyIndex = CursorUtil.getColumnIndex(_cursor, "conversationIdBase58");
      if (_itemKeyIndex == -1) {
        return;
      }
      final int _cursorIndexOfMemberIdBase58 = 0;
      final int _cursorIndexOfConversationIdBase58 = 1;
      final int _cursorIndexOfMemberName = 2;
      final int _cursorIndexOfImageUri = 3;
      final int _cursorIndexOfIsHost = 4;
      while (_cursor.moveToNext()) {
        final String _tmpKey;
        if (_cursor.isNull(_itemKeyIndex)) {
          _tmpKey = null;
        } else {
          _tmpKey = _cursor.getString(_itemKeyIndex);
        }
        if (_tmpKey != null) {
          final ArrayList<ConversationMember> _tmpRelation = _map.get(_tmpKey);
          if (_tmpRelation != null) {
            final ConversationMember _item_1;
            final String _tmpMemberIdBase58;
            if (_cursor.isNull(_cursorIndexOfMemberIdBase58)) {
              _tmpMemberIdBase58 = null;
            } else {
              _tmpMemberIdBase58 = _cursor.getString(_cursorIndexOfMemberIdBase58);
            }
            final String _tmpConversationIdBase58;
            if (_cursor.isNull(_cursorIndexOfConversationIdBase58)) {
              _tmpConversationIdBase58 = null;
            } else {
              _tmpConversationIdBase58 = _cursor.getString(_cursorIndexOfConversationIdBase58);
            }
            final String _tmpMemberName;
            if (_cursor.isNull(_cursorIndexOfMemberName)) {
              _tmpMemberName = null;
            } else {
              _tmpMemberName = _cursor.getString(_cursorIndexOfMemberName);
            }
            final String _tmpImageUri;
            if (_cursor.isNull(_cursorIndexOfImageUri)) {
              _tmpImageUri = null;
            } else {
              _tmpImageUri = _cursor.getString(_cursorIndexOfImageUri);
            }
            final boolean _tmpIsHost;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsHost);
            _tmpIsHost = _tmp != 0;
            _item_1 = new ConversationMember(_tmpMemberIdBase58,_tmpConversationIdBase58,_tmpMemberName,_tmpImageUri,_tmpIsHost);
            _tmpRelation.add(_item_1);
          }
        }
      }
    } finally {
      _cursor.close();
    }
  }

  private void __fetchRelationshipmessageContentsAscomGetcodeModelChatMessageContent(
      @NonNull final ArrayMap<String, ArrayList<MessageContent>> _map) {
    final Set<String> __mapKeySet = _map.keySet();
    if (__mapKeySet.isEmpty()) {
      return;
    }
    if (_map.size() > RoomDatabase.MAX_BIND_PARAMETER_CNT) {
      RelationUtil.recursiveFetchArrayMap(_map, true, (map) -> {
        __fetchRelationshipmessageContentsAscomGetcodeModelChatMessageContent(map);
        return Unit.INSTANCE;
      });
      return;
    }
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT `content`,`messageIdBase58` FROM `message_contents` WHERE `messageIdBase58` IN (");
    final int _inputSize = __mapKeySet == null ? 1 : __mapKeySet.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _stmt = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    if (__mapKeySet == null) {
      _stmt.bindNull(_argIndex);
    } else {
      for (String _item : __mapKeySet) {
        if (_item == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, _item);
        }
        _argIndex++;
      }
    }
    final Cursor _cursor = DBUtil.query(__db, _stmt, false, null);
    try {
      final int _itemKeyIndex = CursorUtil.getColumnIndex(_cursor, "messageIdBase58");
      if (_itemKeyIndex == -1) {
        return;
      }
      while (_cursor.moveToNext()) {
        final String _tmpKey;
        if (_cursor.isNull(_itemKeyIndex)) {
          _tmpKey = null;
        } else {
          _tmpKey = _cursor.getString(_itemKeyIndex);
        }
        if (_tmpKey != null) {
          final ArrayList<MessageContent> _tmpRelation = _map.get(_tmpKey);
          if (_tmpRelation != null) {
            final MessageContent _item_1;
            final String _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(0);
            }
            _item_1 = __sharedConverters.stringToMessageContent(_tmp);
            _tmpRelation.add(_item_1);
          }
        }
      }
    } finally {
      _cursor.close();
    }
  }

  private void __fetchRelationshipmessagesAscomGetcodeOct24DomainModelChatConversationMessageWithContent(
      @NonNull final ArrayMap<String, ConversationMessageWithContent> _map) {
    final Set<String> __mapKeySet = _map.keySet();
    if (__mapKeySet.isEmpty()) {
      return;
    }
    if (_map.size() > RoomDatabase.MAX_BIND_PARAMETER_CNT) {
      RelationUtil.recursiveFetchArrayMap(_map, false, (map) -> {
        __fetchRelationshipmessagesAscomGetcodeOct24DomainModelChatConversationMessageWithContent(map);
        return Unit.INSTANCE;
      });
      return;
    }
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT `idBase58`,`dateMillis`,`senderIdBase58`,`conversationIdBase58` FROM `messages` WHERE `conversationIdBase58` IN (");
    final int _inputSize = __mapKeySet == null ? 1 : __mapKeySet.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _stmt = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    if (__mapKeySet == null) {
      _stmt.bindNull(_argIndex);
    } else {
      for (String _item : __mapKeySet) {
        if (_item == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, _item);
        }
        _argIndex++;
      }
    }
    final Cursor _cursor = DBUtil.query(__db, _stmt, true, null);
    try {
      final int _itemKeyIndex = CursorUtil.getColumnIndex(_cursor, "conversationIdBase58");
      if (_itemKeyIndex == -1) {
        return;
      }
      final int _cursorIndexOfIdBase58 = 0;
      final int _cursorIndexOfDateMillis = 1;
      final int _cursorIndexOfSenderIdBase58 = 2;
      final int _cursorIndexOfConversationIdBase58 = 3;
      final ArrayMap<String, ArrayList<MessageContent>> _collectionContents = new ArrayMap<String, ArrayList<MessageContent>>();
      while (_cursor.moveToNext()) {
        final String _tmpKey;
        if (_cursor.isNull(_cursorIndexOfIdBase58)) {
          _tmpKey = null;
        } else {
          _tmpKey = _cursor.getString(_cursorIndexOfIdBase58);
        }
        if (_tmpKey != null) {
          if (!_collectionContents.containsKey(_tmpKey)) {
            _collectionContents.put(_tmpKey, new ArrayList<MessageContent>());
          }
        }
      }
      _cursor.moveToPosition(-1);
      __fetchRelationshipmessageContentsAscomGetcodeModelChatMessageContent(_collectionContents);
      while (_cursor.moveToNext()) {
        final String _tmpKey_1;
        if (_cursor.isNull(_itemKeyIndex)) {
          _tmpKey_1 = null;
        } else {
          _tmpKey_1 = _cursor.getString(_itemKeyIndex);
        }
        if (_tmpKey_1 != null) {
          if (_map.containsKey(_tmpKey_1)) {
            final ConversationMessageWithContent _item_1;
            final ConversationMessage _tmpMessage;
            final String _tmpIdBase58;
            if (_cursor.isNull(_cursorIndexOfIdBase58)) {
              _tmpIdBase58 = null;
            } else {
              _tmpIdBase58 = _cursor.getString(_cursorIndexOfIdBase58);
            }
            final long _tmpDateMillis;
            _tmpDateMillis = _cursor.getLong(_cursorIndexOfDateMillis);
            final String _tmpSenderIdBase58;
            if (_cursor.isNull(_cursorIndexOfSenderIdBase58)) {
              _tmpSenderIdBase58 = null;
            } else {
              _tmpSenderIdBase58 = _cursor.getString(_cursorIndexOfSenderIdBase58);
            }
            final String _tmpConversationIdBase58;
            if (_cursor.isNull(_cursorIndexOfConversationIdBase58)) {
              _tmpConversationIdBase58 = null;
            } else {
              _tmpConversationIdBase58 = _cursor.getString(_cursorIndexOfConversationIdBase58);
            }
            _tmpMessage = new ConversationMessage(_tmpIdBase58,_tmpConversationIdBase58,_tmpSenderIdBase58,_tmpDateMillis,null);
            final ArrayList<MessageContent> _tmpContentsCollection;
            final String _tmpKey_2;
            if (_cursor.isNull(_cursorIndexOfIdBase58)) {
              _tmpKey_2 = null;
            } else {
              _tmpKey_2 = _cursor.getString(_cursorIndexOfIdBase58);
            }
            if (_tmpKey_2 != null) {
              _tmpContentsCollection = _collectionContents.get(_tmpKey_2);
            } else {
              _tmpContentsCollection = new ArrayList<MessageContent>();
            }
            _item_1 = new ConversationMessageWithContent(_tmpMessage,_tmpContentsCollection);
            _map.put(_tmpKey_1, _item_1);
          }
        }
      }
    } finally {
      _cursor.close();
    }
  }

  private MessageStatus __MessageStatus_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "Sent": return MessageStatus.Sent;
      case "Delivered": return MessageStatus.Delivered;
      case "Read": return MessageStatus.Read;
      case "Unknown": return MessageStatus.Unknown;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }

  private void __fetchRelationshipconversationPointersAscomGetcodeOct24DomainModelChatConversationPointerCrossRef(
      @NonNull final ArrayMap<String, ArrayList<ConversationPointerCrossRef>> _map) {
    final Set<String> __mapKeySet = _map.keySet();
    if (__mapKeySet.isEmpty()) {
      return;
    }
    if (_map.size() > RoomDatabase.MAX_BIND_PARAMETER_CNT) {
      RelationUtil.recursiveFetchArrayMap(_map, true, (map) -> {
        __fetchRelationshipconversationPointersAscomGetcodeOct24DomainModelChatConversationPointerCrossRef(map);
        return Unit.INSTANCE;
      });
      return;
    }
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT `conversationIdBase58`,`messageIdString`,`status` FROM `conversation_pointers` WHERE `conversationIdBase58` IN (");
    final int _inputSize = __mapKeySet == null ? 1 : __mapKeySet.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _stmt = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    if (__mapKeySet == null) {
      _stmt.bindNull(_argIndex);
    } else {
      for (String _item : __mapKeySet) {
        if (_item == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, _item);
        }
        _argIndex++;
      }
    }
    final Cursor _cursor = DBUtil.query(__db, _stmt, false, null);
    try {
      final int _itemKeyIndex = CursorUtil.getColumnIndex(_cursor, "conversationIdBase58");
      if (_itemKeyIndex == -1) {
        return;
      }
      final int _cursorIndexOfConversationIdBase58 = 0;
      final int _cursorIndexOfMessageIdString = 1;
      final int _cursorIndexOfStatus = 2;
      while (_cursor.moveToNext()) {
        final String _tmpKey;
        if (_cursor.isNull(_itemKeyIndex)) {
          _tmpKey = null;
        } else {
          _tmpKey = _cursor.getString(_itemKeyIndex);
        }
        if (_tmpKey != null) {
          final ArrayList<ConversationPointerCrossRef> _tmpRelation = _map.get(_tmpKey);
          if (_tmpRelation != null) {
            final ConversationPointerCrossRef _item_1;
            final String _tmpConversationIdBase58;
            if (_cursor.isNull(_cursorIndexOfConversationIdBase58)) {
              _tmpConversationIdBase58 = null;
            } else {
              _tmpConversationIdBase58 = _cursor.getString(_cursorIndexOfConversationIdBase58);
            }
            final String _tmpMessageIdString;
            if (_cursor.isNull(_cursorIndexOfMessageIdString)) {
              _tmpMessageIdString = null;
            } else {
              _tmpMessageIdString = _cursor.getString(_cursorIndexOfMessageIdString);
            }
            final MessageStatus _tmpStatus;
            _tmpStatus = __MessageStatus_stringToEnum(_cursor.getString(_cursorIndexOfStatus));
            _item_1 = new ConversationPointerCrossRef(_tmpConversationIdBase58,_tmpMessageIdString,_tmpStatus);
            _tmpRelation.add(_item_1);
          }
        }
      }
    } finally {
      _cursor.close();
    }
  }
}
