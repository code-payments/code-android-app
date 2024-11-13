package com.getcode.oct24.internal.db;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000X\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0002\b\u0007\n\u0002\u0010\u0011\n\u0002\b\u0002\ba\u0018\u00002\u00020\u0001J\b\u0010\u0002\u001a\u00020\u0003H\'J\u0010\u0010\u0004\u001a\u00020\u00032\u0006\u0010\u0005\u001a\u00020\u0006H\'J \u0010\u0007\u001a\u00020\u00032\u0010\u0010\b\u001a\f\u0012\u0004\u0012\u00020\n0\tj\u0002`\u000bH\u0096@\u00a2\u0006\u0002\u0010\fJ\u0016\u0010\u0007\u001a\u00020\u00032\u0006\u0010\b\u001a\u00020\rH\u00a7@\u00a2\u0006\u0002\u0010\u000eJ\"\u0010\u000f\u001a\u0004\u0018\u00010\u00102\u0010\u0010\b\u001a\f\u0012\u0004\u0012\u00020\n0\tj\u0002`\u000bH\u0096@\u00a2\u0006\u0002\u0010\fJ\u0018\u0010\u000f\u001a\u0004\u0018\u00010\u00102\u0006\u0010\b\u001a\u00020\rH\u00a7@\u00a2\u0006\u0002\u0010\u000eJ\u0018\u0010\u0011\u001a\u0004\u0018\u00010\u00122\u0006\u0010\u0013\u001a\u00020\rH\u00a7@\u00a2\u0006\u0002\u0010\u000eJ\"\u0010\u0014\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00100\u00152\u0010\u0010\b\u001a\f\u0012\u0004\u0012\u00020\n0\tj\u0002`\u000bH\u0016J\u0018\u0010\u0014\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00100\u00152\u0006\u0010\b\u001a\u00020\rH\'J\u0014\u0010\u0016\u001a\u000e\u0012\u0004\u0012\u00020\u0018\u0012\u0004\u0012\u00020\u00120\u0017H\'J&\u0010\u0019\u001a\u00020\u00032\u0016\u0010\u001a\u001a\u0012\u0012\u000e\u0012\f\u0012\u0004\u0012\u00020\n0\tj\u0002`\u000b0\tH\u0096@\u00a2\u0006\u0002\u0010\fJ\u001c\u0010\u001b\u001a\u00020\u00032\f\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\r0\tH\u00a7@\u00a2\u0006\u0002\u0010\fJ\u0014\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u00060\tH\u00a7@\u00a2\u0006\u0002\u0010\u001dJ \u0010\u001e\u001a\u00020\u00032\u0010\u0010\u0013\u001a\f\u0012\u0004\u0012\u00020\n0\tj\u0002`\u000bH\u0096@\u00a2\u0006\u0002\u0010\fJ\u0016\u0010\u001e\u001a\u00020\u00032\u0006\u0010\u0013\u001a\u00020\rH\u0096@\u00a2\u0006\u0002\u0010\u000eJ\"\u0010\u001f\u001a\u00020\u00032\u0012\u0010\u0005\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u00060 \"\u00020\u0006H\u00a7@\u00a2\u0006\u0002\u0010!\u00a8\u0006\""}, d2 = {"Lcom/getcode/oct24/internal/db/ConversationDao;", "", "clearConversations", "", "deleteConversation", "conversation", "Lcom/getcode/oct24/domain/model/chat/Conversation;", "deleteConversationById", "id", "", "", "Lcom/getcode/model/ID;", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "findConversation", "Lcom/getcode/oct24/domain/model/chat/ConversationWithMembersAndLastPointers;", "getConversationWithMembersAndLastMessage", "Lcom/getcode/oct24/domain/model/chat/ConversationWithMembersAndLastMessage;", "conversationId", "observeConversation", "Lkotlinx/coroutines/flow/Flow;", "observeConversations", "Landroidx/paging/PagingSource;", "", "purgeConversationsNotIn", "chatIds", "purgeConversationsNotInByString", "queryConversations", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "resetUnreadCount", "upsertConversations", "", "([Lcom/getcode/oct24/domain/model/chat/Conversation;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "flipchat_debug"})
@androidx.room.Dao()
public abstract interface ConversationDao {
    
    @androidx.room.Insert(onConflict = 1)
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object upsertConversations(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.model.chat.Conversation[] conversation, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.RewriteQueriesToDropUnusedColumns()
    @androidx.room.Query(value = "\n    SELECT * FROM conversations\n    LEFT JOIN (\n        SELECT conversationIdBase58, MAX(dateMillis) as lastMessageTimestamp \n        FROM messages \n        GROUP BY conversationIdBase58\n    ) AS lastMessages ON conversations.idBase58 = lastMessages.conversationIdBase58\n    WHERE roomNumber > 0\n    ORDER BY lastMessageTimestamp DESC\n    ")
    @org.jetbrains.annotations.NotNull()
    public abstract androidx.paging.PagingSource<java.lang.Integer, com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastMessage> observeConversations();
    
    @androidx.room.RewriteQueriesToDropUnusedColumns()
    @androidx.room.Transaction()
    @androidx.room.Query(value = "\n        SELECT * FROM conversations AS c\n        LEFT JOIN members AS m ON c.idBase58 = m.conversationIdBase58\n        LEFT JOIN conversation_pointers AS p ON c.idBase58 = p.conversationIdBase58\n        WHERE c.idBase58 = :id\n    ")
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers> observeConversation(@org.jetbrains.annotations.NotNull()
    java.lang.String id);
    
    @org.jetbrains.annotations.NotNull()
    public abstract kotlinx.coroutines.flow.Flow<com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers> observeConversation(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> id);
    
    @androidx.room.RewriteQueriesToDropUnusedColumns()
    @androidx.room.Query(value = "SELECT * FROM conversations WHERE idBase58 = :id")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object findConversation(@org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object findConversation(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM conversations")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object queryConversations(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.getcode.oct24.domain.model.chat.Conversation>> $completion);
    
    @androidx.room.Delete()
    public abstract void deleteConversation(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.model.chat.Conversation conversation);
    
    @androidx.room.Query(value = "DELETE FROM conversations WHERE idBase58 = :id")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object deleteConversationById(@org.jetbrains.annotations.NotNull()
    java.lang.String id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object deleteConversationById(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM conversations WHERE idBase58 NOT IN (:chatIds)")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object purgeConversationsNotInByString(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> chatIds, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object purgeConversationsNotIn(@org.jetbrains.annotations.NotNull()
    java.util.List<? extends java.util.List<java.lang.Byte>> chatIds, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM conversations")
    public abstract void clearConversations();
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object resetUnreadCount(@org.jetbrains.annotations.NotNull()
    java.lang.String conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object resetUnreadCount(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Transaction()
    @androidx.room.Query(value = "\n        SELECT * FROM conversations \n        WHERE idBase58 = :conversationId \n    ")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getConversationWithMembersAndLastMessage(@org.jetbrains.annotations.NotNull()
    java.lang.String conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastMessage> $completion);
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
        
        @org.jetbrains.annotations.NotNull()
        public static kotlinx.coroutines.flow.Flow<com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers> observeConversation(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> id) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object findConversation(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> id, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object deleteConversationById(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> id, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object purgeConversationsNotIn(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<? extends java.util.List<java.lang.Byte>> chatIds, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object resetUnreadCount(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationDao $this, @org.jetbrains.annotations.NotNull()
        java.lang.String conversationId, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object resetUnreadCount(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
    }
}