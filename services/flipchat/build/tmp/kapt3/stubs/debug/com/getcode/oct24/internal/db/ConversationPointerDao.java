package com.getcode.oct24.internal.db;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\ba\u0018\u00002\u00020\u0001J\u000e\u0010\u0002\u001a\u00020\u0003H\u00a7@\u00a2\u0006\u0002\u0010\u0004J \u0010\u0005\u001a\u00020\u00032\u0010\u0010\u0006\u001a\f\u0012\u0004\u0012\u00020\b0\u0007j\u0002`\tH\u0096@\u00a2\u0006\u0002\u0010\nJ\u0016\u0010\u0005\u001a\u00020\u00032\u0006\u0010\u0006\u001a\u00020\u000bH\u00a7@\u00a2\u0006\u0002\u0010\fJ0\u0010\r\u001a\u00020\u00032\u0010\u0010\u000e\u001a\f\u0012\u0004\u0012\u00020\b0\u0007j\u0002`\t2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0012H\u0096@\u00a2\u0006\u0002\u0010\u0013J\u0016\u0010\r\u001a\u00020\u00032\u0006\u0010\u0014\u001a\u00020\u0015H\u00a7@\u00a2\u0006\u0002\u0010\u0016J&\u0010\u0017\u001a\u00020\u00032\u0016\u0010\u0018\u001a\u0012\u0012\u000e\u0012\f\u0012\u0004\u0012\u00020\b0\u0007j\u0002`\t0\u0007H\u0096@\u00a2\u0006\u0002\u0010\nJ\u001c\u0010\u0019\u001a\u00020\u00032\f\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\u000b0\u0007H\u00a7@\u00a2\u0006\u0002\u0010\nJ\u0014\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u00150\u0007H\u00a7@\u00a2\u0006\u0002\u0010\u0004\u00a8\u0006\u001b"}, d2 = {"Lcom/getcode/oct24/internal/db/ConversationPointerDao;", "", "clearMapping", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "deletePointerForConversation", "id", "", "", "Lcom/getcode/model/ID;", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "insert", "conversationId", "messageId", "Ljava/util/UUID;", "status", "Lcom/getcode/model/chat/MessageStatus;", "(Ljava/util/List;Ljava/util/UUID;Lcom/getcode/model/chat/MessageStatus;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "crossRef", "Lcom/getcode/oct24/domain/model/chat/ConversationPointerCrossRef;", "(Lcom/getcode/oct24/domain/model/chat/ConversationPointerCrossRef;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "purgePointersNoLongerNeeded", "chatIds", "purgePointersNoLongerNeededByString", "queryPointers", "flipchat_debug"})
@androidx.room.Dao()
public abstract interface ConversationPointerDao {
    
    @androidx.room.Insert(onConflict = 1)
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object insert(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.model.chat.ConversationPointerCrossRef crossRef, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object insert(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
    java.util.UUID messageId, @org.jetbrains.annotations.NotNull()
    com.getcode.model.chat.MessageStatus status, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM conversation_pointers")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object queryPointers(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.getcode.oct24.domain.model.chat.ConversationPointerCrossRef>> $completion);
    
    @androidx.room.Query(value = "DELETE FROM conversation_pointers WHERE conversationIdBase58 = :id")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object deletePointerForConversation(@org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object deletePointerForConversation(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM conversation_pointers WHERE conversationIdBase58 NOT IN (:chatIds)")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object purgePointersNoLongerNeededByString(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> chatIds, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object purgePointersNoLongerNeeded(@org.jetbrains.annotations.NotNull()
    java.util.List<? extends java.util.List<java.lang.Byte>> chatIds, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM conversation_pointers")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object clearMapping(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object insert(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationPointerDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
        java.util.UUID messageId, @org.jetbrains.annotations.NotNull()
        com.getcode.model.chat.MessageStatus status, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object deletePointerForConversation(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationPointerDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> id, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object purgePointersNoLongerNeeded(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationPointerDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<? extends java.util.List<java.lang.Byte>> chatIds, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
    }
}