package com.getcode.oct24.internal.db;

import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.getcode.oct24.domain.model.chat.ConversationMember;
import java.lang.Byte;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ConversationMemberDao_Impl implements ConversationMemberDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ConversationMember> __insertionAdapterOfConversationMember;

  private final SharedSQLiteStatement __preparedStmtOfRemoveMembersFrom;

  private final SharedSQLiteStatement __preparedStmtOfRemoveMemberFromConversation;

  private final SharedSQLiteStatement __preparedStmtOfClearConversations;

  public ConversationMemberDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfConversationMember = new EntityInsertionAdapter<ConversationMember>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `members` (`memberIdBase58`,`conversationIdBase58`,`memberName`,`imageUri`,`isHost`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ConversationMember entity) {
        if (entity.getMemberIdBase58() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getMemberIdBase58());
        }
        if (entity.getConversationIdBase58() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getConversationIdBase58());
        }
        if (entity.getMemberName() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getMemberName());
        }
        if (entity.getImageUri() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getImageUri());
        }
        final int _tmp = entity.isHost() ? 1 : 0;
        statement.bindLong(5, _tmp);
      }
    };
    this.__preparedStmtOfRemoveMembersFrom = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM members WHERE conversationIdBase58 = ?";
        return _query;
      }
    };
    this.__preparedStmtOfRemoveMemberFromConversation = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM members WHERE memberIdBase58 = ? AND conversationIdBase58 = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearConversations = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM members";
        return _query;
      }
    };
  }

  @Override
  public Object upsertMembers(final ConversationMember[] members,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfConversationMember.insert(members);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object removeMembersFrom(final String conversationId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRemoveMembersFrom.acquire();
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
          __preparedStmtOfRemoveMembersFrom.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object removeMemberFromConversation(final String memberId, final String conversationId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRemoveMemberFromConversation.acquire();
        int _argIndex = 1;
        if (memberId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, memberId);
        }
        _argIndex = 2;
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
          __preparedStmtOfRemoveMemberFromConversation.release(_stmt);
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
  public Object purgeMembersNotInByString(final String conversationId, final List<String> memberIds,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("DELETE FROM members WHERE memberIdBase58 NOT IN (");
        final int _inputSize = memberIds.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(") AND conversationIdBase58 = ");
        _stringBuilder.append("?");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (String _item : memberIds) {
          if (_item == null) {
            _stmt.bindNull(_argIndex);
          } else {
            _stmt.bindString(_argIndex, _item);
          }
          _argIndex++;
        }
        _argIndex = 1 + _inputSize;
        if (conversationId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, conversationId);
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
  public Object removeMembersFrom(final List<Byte> conversationId,
      final Continuation<? super Unit> $completion) {
    return ConversationMemberDao.DefaultImpls.removeMembersFrom(ConversationMemberDao_Impl.this, conversationId, $completion);
  }

  @Override
  public Object removeMemberFromConversation(final List<Byte> memberId,
      final List<Byte> conversationId, final Continuation<? super Unit> $completion) {
    return ConversationMemberDao.DefaultImpls.removeMemberFromConversation(ConversationMemberDao_Impl.this, memberId, conversationId, $completion);
  }

  @Override
  public Object refreshMembers(final List<Byte> conversationId,
      final List<ConversationMember> members, final Continuation<? super Unit> $completion) {
    return ConversationMemberDao.DefaultImpls.refreshMembers(ConversationMemberDao_Impl.this, conversationId, members, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
