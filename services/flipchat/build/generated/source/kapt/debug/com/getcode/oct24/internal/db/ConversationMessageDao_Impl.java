package com.getcode.oct24.internal.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.paging.PagingSource;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomDatabaseKt;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.paging.LimitOffsetPagingSource;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.RelationUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.getcode.model.chat.MessageContent;
import com.getcode.oct24.domain.model.chat.ConversationMember;
import com.getcode.oct24.domain.model.chat.ConversationMessage;
import com.getcode.oct24.domain.model.chat.ConversationMessageContent;
import com.getcode.oct24.domain.model.chat.ConversationMessageWithContent;
import com.getcode.oct24.domain.model.chat.ConversationMessageWithContentAndMember;
import com.getcode.services.db.SharedConverters;
import java.lang.Boolean;
import java.lang.Byte;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
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

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ConversationMessageDao_Impl implements ConversationMessageDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ConversationMessage> __insertionAdapterOfConversationMessage;

  private final EntityInsertionAdapter<ConversationMessageContent> __insertionAdapterOfConversationMessageContent;

  private final SharedConverters __sharedConverters = new SharedConverters();

  private final SharedSQLiteStatement __preparedStmtOfRemoveForConversation;

  private final SharedSQLiteStatement __preparedStmtOfMarkDeleted;

  private final SharedSQLiteStatement __preparedStmtOfRemoveContentsForMessage;

  private final SharedSQLiteStatement __preparedStmtOfClearMessages;

  private final SharedSQLiteStatement __preparedStmtOfClearMessagesForChat;

  public ConversationMessageDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfConversationMessage = new EntityInsertionAdapter<ConversationMessage>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `messages` (`idBase58`,`conversationIdBase58`,`senderIdBase58`,`dateMillis`,`deleted`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ConversationMessage entity) {
        if (entity.getIdBase58() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getIdBase58());
        }
        if (entity.getConversationIdBase58() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getConversationIdBase58());
        }
        if (entity.getSenderIdBase58() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getSenderIdBase58());
        }
        statement.bindLong(4, entity.getDateMillis());
        final int _tmp = entity.isDeleted() ? 1 : 0;
        statement.bindLong(5, _tmp);
      }
    };
    this.__insertionAdapterOfConversationMessageContent = new EntityInsertionAdapter<ConversationMessageContent>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `message_contents` (`messageIdBase58`,`content`) VALUES (?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ConversationMessageContent entity) {
        if (entity.getMessageIdBase58() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getMessageIdBase58());
        }
        final String _tmp = __sharedConverters.messageContentToString(entity.getContent());
        if (_tmp == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, _tmp);
        }
      }
    };
    this.__preparedStmtOfRemoveForConversation = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM messages WHERE conversationIdBase58 = ?";
        return _query;
      }
    };
    this.__preparedStmtOfMarkDeleted = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE messages SET deleted = 1 WHERE idBase58 = ?";
        return _query;
      }
    };
    this.__preparedStmtOfRemoveContentsForMessage = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM message_contents WHERE messageIdBase58 = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearMessages = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM messages";
        return _query;
      }
    };
    this.__preparedStmtOfClearMessagesForChat = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM messages WHERE conversationIdBase58 = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsertMessages(final ConversationMessage[] message,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfConversationMessage.insert(message);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertMessageContent(final ConversationMessageContent[] content,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfConversationMessageContent.insert(content);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertMessagesWithContent(final ConversationMessageWithContent[] message,
      final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> ConversationMessageDao.DefaultImpls.upsertMessagesWithContent(ConversationMessageDao_Impl.this, message, __cont), $completion);
  }

  @Override
  public Object upsertMessagesWithContent(final List<ConversationMessageWithContent> messages,
      final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> ConversationMessageDao.DefaultImpls.upsertMessagesWithContent(ConversationMessageDao_Impl.this, messages, __cont), $completion);
  }

  @Override
  public Object removeForConversation(final String conversationId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRemoveForConversation.acquire();
        int _argIndex = 1;
        if (conversationId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, conversationId);
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
          __preparedStmtOfRemoveForConversation.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object markDeleted(final String messageId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkDeleted.acquire();
        int _argIndex = 1;
        if (messageId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, messageId);
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
          __preparedStmtOfMarkDeleted.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object removeContentsForMessage(final String messageId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRemoveContentsForMessage.acquire();
        int _argIndex = 1;
        if (messageId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, messageId);
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
          __preparedStmtOfRemoveContentsForMessage.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public void clearMessages() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfClearMessages.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfClearMessages.release(_stmt);
    }
  }

  @Override
  public Object clearMessagesForChat(final String chatId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearMessagesForChat.acquire();
        int _argIndex = 1;
        if (chatId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, chatId);
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
          __preparedStmtOfClearMessagesForChat.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public PagingSource<Integer, ConversationMessageWithContentAndMember> observeConversationMessages(
      final String id) {
    final String _sql = "\n"
            + "        SELECT DISTINCT * FROM messages\n"
            + "        LEFT JOIN message_contents ON messages.idBase58 = message_contents.messageIdBase58\n"
            + "        LEFT JOIN members ON messages.senderIdBase58 = members.memberIdBase58 AND messages.conversationIdBase58 = members.conversationIdBase58\n"
            + "        WHERE messages.conversationIdBase58 = ?\n"
            + "        ORDER BY dateMillis DESC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (id == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, id);
    }
    return new LimitOffsetPagingSource<ConversationMessageWithContentAndMember>(_statement, __db, "message_contents", "members", "messages") {
      @Override
      @NonNull
      protected List<ConversationMessageWithContentAndMember> convertRows(
          @NonNull final Cursor cursor) {
        final int _cursorIndexOfIdBase58 = CursorUtil.getColumnIndexOrThrow(cursor, "idBase58");
        final int _cursorIndexOfConversationIdBase58 = CursorUtil.getColumnIndexOrThrow(cursor, "conversationIdBase58");
        final int _cursorIndexOfSenderIdBase58 = CursorUtil.getColumnIndexOrThrow(cursor, "senderIdBase58");
        final int _cursorIndexOfDateMillis = CursorUtil.getColumnIndexOrThrow(cursor, "dateMillis");
        final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(cursor, "deleted");
        final ArrayMap<String, ArrayList<MessageContent>> _collectionContents = new ArrayMap<String, ArrayList<MessageContent>>();
        final ArrayMap<String, ConversationMember> _collectionMember = new ArrayMap<String, ConversationMember>();
        while (cursor.moveToNext()) {
          final String _tmpKey;
          if (cursor.isNull(_cursorIndexOfIdBase58)) {
            _tmpKey = null;
          } else {
            _tmpKey = cursor.getString(_cursorIndexOfIdBase58);
          }
          if (_tmpKey != null) {
            if (!_collectionContents.containsKey(_tmpKey)) {
              _collectionContents.put(_tmpKey, new ArrayList<MessageContent>());
            }
          }
          final String _tmpKey_1;
          if (cursor.isNull(_cursorIndexOfSenderIdBase58)) {
            _tmpKey_1 = null;
          } else {
            _tmpKey_1 = cursor.getString(_cursorIndexOfSenderIdBase58);
          }
          if (_tmpKey_1 != null) {
            _collectionMember.put(_tmpKey_1, null);
          }
        }
        cursor.moveToPosition(-1);
        __fetchRelationshipmessageContentsAscomGetcodeModelChatMessageContent(_collectionContents);
        __fetchRelationshipmembersAscomGetcodeOct24DomainModelChatConversationMember(_collectionMember);
        final List<ConversationMessageWithContentAndMember> _result = new ArrayList<ConversationMessageWithContentAndMember>(cursor.getCount());
        while (cursor.moveToNext()) {
          final ConversationMessageWithContentAndMember _item;
          final ConversationMessage _tmpMessage;
          final String _tmpIdBase58;
          if (cursor.isNull(_cursorIndexOfIdBase58)) {
            _tmpIdBase58 = null;
          } else {
            _tmpIdBase58 = cursor.getString(_cursorIndexOfIdBase58);
          }
          final String _tmpConversationIdBase58;
          if (cursor.isNull(_cursorIndexOfConversationIdBase58)) {
            _tmpConversationIdBase58 = null;
          } else {
            _tmpConversationIdBase58 = cursor.getString(_cursorIndexOfConversationIdBase58);
          }
          final String _tmpSenderIdBase58;
          if (cursor.isNull(_cursorIndexOfSenderIdBase58)) {
            _tmpSenderIdBase58 = null;
          } else {
            _tmpSenderIdBase58 = cursor.getString(_cursorIndexOfSenderIdBase58);
          }
          final long _tmpDateMillis;
          _tmpDateMillis = cursor.getLong(_cursorIndexOfDateMillis);
          final Boolean _tmpDeleted;
          final Integer _tmp;
          if (cursor.isNull(_cursorIndexOfDeleted)) {
            _tmp = null;
          } else {
            _tmp = cursor.getInt(_cursorIndexOfDeleted);
          }
          _tmpDeleted = _tmp == null ? null : _tmp != 0;
          _tmpMessage = new ConversationMessage(_tmpIdBase58,_tmpConversationIdBase58,_tmpSenderIdBase58,_tmpDateMillis,_tmpDeleted);
          final ArrayList<MessageContent> _tmpContentsCollection;
          final String _tmpKey_2;
          if (cursor.isNull(_cursorIndexOfIdBase58)) {
            _tmpKey_2 = null;
          } else {
            _tmpKey_2 = cursor.getString(_cursorIndexOfIdBase58);
          }
          if (_tmpKey_2 != null) {
            _tmpContentsCollection = _collectionContents.get(_tmpKey_2);
          } else {
            _tmpContentsCollection = new ArrayList<MessageContent>();
          }
          final ConversationMember _tmpMember;
          final String _tmpKey_3;
          if (cursor.isNull(_cursorIndexOfSenderIdBase58)) {
            _tmpKey_3 = null;
          } else {
            _tmpKey_3 = cursor.getString(_cursorIndexOfSenderIdBase58);
          }
          if (_tmpKey_3 != null) {
            _tmpMember = _collectionMember.get(_tmpKey_3);
          } else {
            _tmpMember = null;
          }
          _item = new ConversationMessageWithContentAndMember(_tmpMessage,_tmpContentsCollection,_tmpMember);
          _result.add(_item);
        }
        return _result;
      }
    };
  }

  @Override
  public Object queryMessages(final String conversationId,
      final Continuation<? super List<ConversationMessage>> $completion) {
    final String _sql = "SELECT * FROM messages WHERE conversationIdBase58 = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (conversationId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, conversationId);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ConversationMessage>>() {
      @Override
      @NonNull
      public List<ConversationMessage> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfIdBase58 = CursorUtil.getColumnIndexOrThrow(_cursor, "idBase58");
          final int _cursorIndexOfConversationIdBase58 = CursorUtil.getColumnIndexOrThrow(_cursor, "conversationIdBase58");
          final int _cursorIndexOfSenderIdBase58 = CursorUtil.getColumnIndexOrThrow(_cursor, "senderIdBase58");
          final int _cursorIndexOfDateMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "dateMillis");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final List<ConversationMessage> _result = new ArrayList<ConversationMessage>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ConversationMessage _item;
            final String _tmpIdBase58;
            if (_cursor.isNull(_cursorIndexOfIdBase58)) {
              _tmpIdBase58 = null;
            } else {
              _tmpIdBase58 = _cursor.getString(_cursorIndexOfIdBase58);
            }
            final String _tmpConversationIdBase58;
            if (_cursor.isNull(_cursorIndexOfConversationIdBase58)) {
              _tmpConversationIdBase58 = null;
            } else {
              _tmpConversationIdBase58 = _cursor.getString(_cursorIndexOfConversationIdBase58);
            }
            final String _tmpSenderIdBase58;
            if (_cursor.isNull(_cursorIndexOfSenderIdBase58)) {
              _tmpSenderIdBase58 = null;
            } else {
              _tmpSenderIdBase58 = _cursor.getString(_cursorIndexOfSenderIdBase58);
            }
            final long _tmpDateMillis;
            _tmpDateMillis = _cursor.getLong(_cursorIndexOfDateMillis);
            final Boolean _tmpDeleted;
            final Integer _tmp;
            if (_cursor.isNull(_cursorIndexOfDeleted)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(_cursorIndexOfDeleted);
            }
            _tmpDeleted = _tmp == null ? null : _tmp != 0;
            _item = new ConversationMessage(_tmpIdBase58,_tmpConversationIdBase58,_tmpSenderIdBase58,_tmpDateMillis,_tmpDeleted);
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
  public Object getNewestMessage(final String conversationId,
      final Continuation<? super ConversationMessage> $completion) {
    final String _sql = "SELECT * FROM messages WHERE conversationIdBase58 = ? ORDER BY dateMillis DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (conversationId == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, conversationId);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ConversationMessage>() {
      @Override
      @Nullable
      public ConversationMessage call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfIdBase58 = CursorUtil.getColumnIndexOrThrow(_cursor, "idBase58");
          final int _cursorIndexOfConversationIdBase58 = CursorUtil.getColumnIndexOrThrow(_cursor, "conversationIdBase58");
          final int _cursorIndexOfSenderIdBase58 = CursorUtil.getColumnIndexOrThrow(_cursor, "senderIdBase58");
          final int _cursorIndexOfDateMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "dateMillis");
          final int _cursorIndexOfDeleted = CursorUtil.getColumnIndexOrThrow(_cursor, "deleted");
          final ConversationMessage _result;
          if (_cursor.moveToFirst()) {
            final String _tmpIdBase58;
            if (_cursor.isNull(_cursorIndexOfIdBase58)) {
              _tmpIdBase58 = null;
            } else {
              _tmpIdBase58 = _cursor.getString(_cursorIndexOfIdBase58);
            }
            final String _tmpConversationIdBase58;
            if (_cursor.isNull(_cursorIndexOfConversationIdBase58)) {
              _tmpConversationIdBase58 = null;
            } else {
              _tmpConversationIdBase58 = _cursor.getString(_cursorIndexOfConversationIdBase58);
            }
            final String _tmpSenderIdBase58;
            if (_cursor.isNull(_cursorIndexOfSenderIdBase58)) {
              _tmpSenderIdBase58 = null;
            } else {
              _tmpSenderIdBase58 = _cursor.getString(_cursorIndexOfSenderIdBase58);
            }
            final long _tmpDateMillis;
            _tmpDateMillis = _cursor.getLong(_cursorIndexOfDateMillis);
            final Boolean _tmpDeleted;
            final Integer _tmp;
            if (_cursor.isNull(_cursorIndexOfDeleted)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(_cursorIndexOfDeleted);
            }
            _tmpDeleted = _tmp == null ? null : _tmp != 0;
            _result = new ConversationMessage(_tmpIdBase58,_tmpConversationIdBase58,_tmpSenderIdBase58,_tmpDateMillis,_tmpDeleted);
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
  public Object purgeMessagesNotInByString(final List<String> chatIds,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("DELETE FROM messages WHERE conversationIdBase58 NOT IN (");
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
  public Object upsertMessages(final List<ConversationMessage> message,
      final Continuation<? super Unit> $completion) {
    return ConversationMessageDao.DefaultImpls.upsertMessages(ConversationMessageDao_Impl.this, message, $completion);
  }

  @Override
  public Object upsertMessageContent(final List<Byte> messageId,
      final List<? extends MessageContent> contents, final Continuation<? super Unit> $completion) {
    return ConversationMessageDao.DefaultImpls.upsertMessageContent(ConversationMessageDao_Impl.this, messageId, contents, $completion);
  }

  @Override
  public PagingSource<Integer, ConversationMessageWithContentAndMember> observeConversationMessages(
      final List<Byte> id) {
    return ConversationMessageDao.DefaultImpls.observeConversationMessages(ConversationMessageDao_Impl.this, id);
  }

  @Override
  public Object queryMessages(final List<Byte> conversationId,
      final Continuation<? super List<ConversationMessage>> $completion) {
    return ConversationMessageDao.DefaultImpls.queryMessages(ConversationMessageDao_Impl.this, conversationId, $completion);
  }

  @Override
  public Object getNewestMessage(final List<Byte> conversationId,
      final Continuation<? super ConversationMessage> $completion) {
    return ConversationMessageDao.DefaultImpls.getNewestMessage(ConversationMessageDao_Impl.this, conversationId, $completion);
  }

  @Override
  public Object removeForConversation(final List<Byte> conversationId,
      final Continuation<? super Unit> $completion) {
    return ConversationMessageDao.DefaultImpls.removeForConversation(ConversationMessageDao_Impl.this, conversationId, $completion);
  }

  @Override
  public Object markDeleted(final List<Byte> messageId,
      final Continuation<? super Unit> $completion) {
    return ConversationMessageDao.DefaultImpls.markDeleted(ConversationMessageDao_Impl.this, messageId, $completion);
  }

  @Override
  public Object removeContentsForMessage(final List<Byte> messageId,
      final Continuation<? super Unit> $completion) {
    return ConversationMessageDao.DefaultImpls.removeContentsForMessage(ConversationMessageDao_Impl.this, messageId, $completion);
  }

  @Override
  public Object markDeletedAndRemoveContents(final List<Byte> messageId,
      final Continuation<? super Unit> $completion) {
    return ConversationMessageDao.DefaultImpls.markDeletedAndRemoveContents(ConversationMessageDao_Impl.this, messageId, $completion);
  }

  @Override
  public Object purgeMessagesNotIn(final List<? extends List<Byte>> chatIds,
      final Continuation<? super Unit> $completion) {
    return ConversationMessageDao.DefaultImpls.purgeMessagesNotIn(ConversationMessageDao_Impl.this, chatIds, $completion);
  }

  @Override
  public Object clearMessagesForChat(final List<Byte> chatId,
      final Continuation<? super Unit> $completion) {
    return ConversationMessageDao.DefaultImpls.clearMessagesForChat(ConversationMessageDao_Impl.this, chatId, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
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

  private void __fetchRelationshipmembersAscomGetcodeOct24DomainModelChatConversationMember(
      @NonNull final ArrayMap<String, ConversationMember> _map) {
    final Set<String> __mapKeySet = _map.keySet();
    if (__mapKeySet.isEmpty()) {
      return;
    }
    if (_map.size() > RoomDatabase.MAX_BIND_PARAMETER_CNT) {
      RelationUtil.recursiveFetchArrayMap(_map, false, (map) -> {
        __fetchRelationshipmembersAscomGetcodeOct24DomainModelChatConversationMember(map);
        return Unit.INSTANCE;
      });
      return;
    }
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT `memberIdBase58`,`conversationIdBase58`,`memberName`,`imageUri`,`isHost` FROM `members` WHERE `memberIdBase58` IN (");
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
      final int _itemKeyIndex = CursorUtil.getColumnIndex(_cursor, "memberIdBase58");
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
          if (_map.containsKey(_tmpKey)) {
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
            _map.put(_tmpKey, _item_1);
          }
        }
      }
    } finally {
      _cursor.close();
    }
  }
}
