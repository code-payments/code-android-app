package com.getcode.oct24.internal.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.getcode.model.chat.MessageStatus;
import com.getcode.oct24.domain.model.chat.ConversationPointerCrossRef;
import java.lang.Byte;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalArgumentException;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ConversationPointerDao_Impl implements ConversationPointerDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ConversationPointerCrossRef> __insertionAdapterOfConversationPointerCrossRef;

  private final SharedSQLiteStatement __preparedStmtOfDeletePointerForConversation;

  private final SharedSQLiteStatement __preparedStmtOfClearMapping;

  public ConversationPointerDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfConversationPointerCrossRef = new EntityInsertionAdapter<ConversationPointerCrossRef>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `conversation_pointers` (`conversationIdBase58`,`messageIdString`,`status`) VALUES (?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ConversationPointerCrossRef entity) {
        if (entity.getConversationIdBase58() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getConversationIdBase58());
        }
        if (entity.getMessageIdString() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getMessageIdString());
        }
        statement.bindString(3, __MessageStatus_enumToString(entity.getStatus()));
      }
    };
    this.__preparedStmtOfDeletePointerForConversation = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM conversation_pointers WHERE conversationIdBase58 = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearMapping = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM conversation_pointers";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final ConversationPointerCrossRef crossRef,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfConversationPointerCrossRef.insert(crossRef);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deletePointerForConversation(final String id,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeletePointerForConversation.acquire();
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
          __preparedStmtOfDeletePointerForConversation.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearMapping(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearMapping.acquire();
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
          __preparedStmtOfClearMapping.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object queryPointers(
      final Continuation<? super List<ConversationPointerCrossRef>> $completion) {
    final String _sql = "SELECT * FROM conversation_pointers";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ConversationPointerCrossRef>>() {
      @Override
      @NonNull
      public List<ConversationPointerCrossRef> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfConversationIdBase58 = CursorUtil.getColumnIndexOrThrow(_cursor, "conversationIdBase58");
          final int _cursorIndexOfMessageIdString = CursorUtil.getColumnIndexOrThrow(_cursor, "messageIdString");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final List<ConversationPointerCrossRef> _result = new ArrayList<ConversationPointerCrossRef>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ConversationPointerCrossRef _item;
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
            _item = new ConversationPointerCrossRef(_tmpConversationIdBase58,_tmpMessageIdString,_tmpStatus);
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
  public Object purgePointersNoLongerNeededByString(final List<String> chatIds,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("DELETE FROM conversation_pointers WHERE conversationIdBase58 NOT IN (");
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
  public Object insert(final List<Byte> conversationId, final UUID messageId,
      final MessageStatus status, final Continuation<? super Unit> $completion) {
    return ConversationPointerDao.DefaultImpls.insert(ConversationPointerDao_Impl.this, conversationId, messageId, status, $completion);
  }

  @Override
  public Object deletePointerForConversation(final List<Byte> id,
      final Continuation<? super Unit> $completion) {
    return ConversationPointerDao.DefaultImpls.deletePointerForConversation(ConversationPointerDao_Impl.this, id, $completion);
  }

  @Override
  public Object purgePointersNoLongerNeeded(final List<? extends List<Byte>> chatIds,
      final Continuation<? super Unit> $completion) {
    return ConversationPointerDao.DefaultImpls.purgePointersNoLongerNeeded(ConversationPointerDao_Impl.this, chatIds, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }

  private String __MessageStatus_enumToString(@NonNull final MessageStatus _value) {
    switch (_value) {
      case Sent: return "Sent";
      case Delivered: return "Delivered";
      case Read: return "Read";
      case Unknown: return "Unknown";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
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
}
