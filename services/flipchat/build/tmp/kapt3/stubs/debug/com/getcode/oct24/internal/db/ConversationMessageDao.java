package com.getcode.oct24.internal.db;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\\\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0011\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\ba\u0018\u00002\u00020\u0001J\b\u0010\u0002\u001a\u00020\u0003H\'J \u0010\u0004\u001a\u00020\u00032\u0010\u0010\u0005\u001a\f\u0012\u0004\u0012\u00020\u00070\u0006j\u0002`\bH\u0096@\u00a2\u0006\u0002\u0010\tJ\u0016\u0010\u0004\u001a\u00020\u00032\u0006\u0010\u0005\u001a\u00020\nH\u00a7@\u00a2\u0006\u0002\u0010\u000bJ\"\u0010\f\u001a\u0004\u0018\u00010\r2\u0010\u0010\u000e\u001a\f\u0012\u0004\u0012\u00020\u00070\u0006j\u0002`\bH\u0096@\u00a2\u0006\u0002\u0010\tJ\u0018\u0010\f\u001a\u0004\u0018\u00010\r2\u0006\u0010\u000e\u001a\u00020\nH\u00a7@\u00a2\u0006\u0002\u0010\u000bJ \u0010\u000f\u001a\u00020\u00032\u0010\u0010\u0010\u001a\f\u0012\u0004\u0012\u00020\u00070\u0006j\u0002`\bH\u0096@\u00a2\u0006\u0002\u0010\tJ\u0016\u0010\u000f\u001a\u00020\u00032\u0006\u0010\u0010\u001a\u00020\nH\u00a7@\u00a2\u0006\u0002\u0010\u000bJ \u0010\u0011\u001a\u00020\u00032\u0010\u0010\u0010\u001a\f\u0012\u0004\u0012\u00020\u00070\u0006j\u0002`\bH\u0096@\u00a2\u0006\u0002\u0010\tJ&\u0010\u0012\u001a\u000e\u0012\u0004\u0012\u00020\u0014\u0012\u0004\u0012\u00020\u00150\u00132\u0010\u0010\u0016\u001a\f\u0012\u0004\u0012\u00020\u00070\u0006j\u0002`\bH\u0016J\u001c\u0010\u0012\u001a\u000e\u0012\u0004\u0012\u00020\u0014\u0012\u0004\u0012\u00020\u00150\u00132\u0006\u0010\u0016\u001a\u00020\nH\'J&\u0010\u0017\u001a\u00020\u00032\u0016\u0010\u0018\u001a\u0012\u0012\u000e\u0012\f\u0012\u0004\u0012\u00020\u00070\u0006j\u0002`\b0\u0006H\u0096@\u00a2\u0006\u0002\u0010\tJ\u001c\u0010\u0019\u001a\u00020\u00032\f\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\n0\u0006H\u00a7@\u00a2\u0006\u0002\u0010\tJ&\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\r0\u00062\u0010\u0010\u000e\u001a\f\u0012\u0004\u0012\u00020\u00070\u0006j\u0002`\bH\u0096@\u00a2\u0006\u0002\u0010\tJ\u001c\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\r0\u00062\u0006\u0010\u000e\u001a\u00020\nH\u00a7@\u00a2\u0006\u0002\u0010\u000bJ \u0010\u001b\u001a\u00020\u00032\u0010\u0010\u0010\u001a\f\u0012\u0004\u0012\u00020\u00070\u0006j\u0002`\bH\u0096@\u00a2\u0006\u0002\u0010\tJ\u0016\u0010\u001b\u001a\u00020\u00032\u0006\u0010\u0010\u001a\u00020\nH\u00a7@\u00a2\u0006\u0002\u0010\u000bJ \u0010\u001c\u001a\u00020\u00032\u0010\u0010\u000e\u001a\f\u0012\u0004\u0012\u00020\u00070\u0006j\u0002`\bH\u0096@\u00a2\u0006\u0002\u0010\tJ\u0016\u0010\u001c\u001a\u00020\u00032\u0006\u0010\u000e\u001a\u00020\nH\u00a7@\u00a2\u0006\u0002\u0010\u000bJ.\u0010\u001d\u001a\u00020\u00032\u0010\u0010\u0010\u001a\f\u0012\u0004\u0012\u00020\u00070\u0006j\u0002`\b2\f\u0010\u001e\u001a\b\u0012\u0004\u0012\u00020\u001f0\u0006H\u0096@\u00a2\u0006\u0002\u0010 J\"\u0010\u001d\u001a\u00020\u00032\u0012\u0010!\u001a\n\u0012\u0006\b\u0001\u0012\u00020#0\"\"\u00020#H\u00a7@\u00a2\u0006\u0002\u0010$J\"\u0010%\u001a\u00020\u00032\u0012\u0010&\u001a\n\u0012\u0006\b\u0001\u0012\u00020\r0\"\"\u00020\rH\u00a7@\u00a2\u0006\u0002\u0010\'J\u001c\u0010%\u001a\u00020\u00032\f\u0010&\u001a\b\u0012\u0004\u0012\u00020\r0\u0006H\u0096@\u00a2\u0006\u0002\u0010\tJ\"\u0010(\u001a\u00020\u00032\u0012\u0010&\u001a\n\u0012\u0006\b\u0001\u0012\u00020)0\"\"\u00020)H\u0097@\u00a2\u0006\u0002\u0010*J\u001c\u0010(\u001a\u00020\u00032\f\u0010+\u001a\b\u0012\u0004\u0012\u00020)0\u0006H\u0097@\u00a2\u0006\u0002\u0010\t\u00a8\u0006,"}, d2 = {"Lcom/getcode/oct24/internal/db/ConversationMessageDao;", "", "clearMessages", "", "clearMessagesForChat", "chatId", "", "", "Lcom/getcode/model/ID;", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getNewestMessage", "Lcom/getcode/oct24/domain/model/chat/ConversationMessage;", "conversationId", "markDeleted", "messageId", "markDeletedAndRemoveContents", "observeConversationMessages", "Landroidx/paging/PagingSource;", "", "Lcom/getcode/oct24/domain/model/chat/ConversationMessageWithContentAndMember;", "id", "purgeMessagesNotIn", "chatIds", "purgeMessagesNotInByString", "queryMessages", "removeContentsForMessage", "removeForConversation", "upsertMessageContent", "contents", "Lcom/getcode/model/chat/MessageContent;", "(Ljava/util/List;Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "content", "", "Lcom/getcode/oct24/domain/model/chat/ConversationMessageContent;", "([Lcom/getcode/oct24/domain/model/chat/ConversationMessageContent;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "upsertMessages", "message", "([Lcom/getcode/oct24/domain/model/chat/ConversationMessage;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "upsertMessagesWithContent", "Lcom/getcode/oct24/domain/model/chat/ConversationMessageWithContent;", "([Lcom/getcode/oct24/domain/model/chat/ConversationMessageWithContent;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "messages", "flipchat_debug"})
@androidx.room.Dao()
public abstract interface ConversationMessageDao {
    
    @androidx.room.Insert(onConflict = 1)
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object upsertMessages(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.model.chat.ConversationMessage[] message, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object upsertMessages(@org.jetbrains.annotations.NotNull()
    java.util.List<com.getcode.oct24.domain.model.chat.ConversationMessage> message, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Insert(onConflict = 1)
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object upsertMessageContent(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.model.chat.ConversationMessageContent[] content, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object upsertMessageContent(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> messageId, @org.jetbrains.annotations.NotNull()
    java.util.List<? extends com.getcode.model.chat.MessageContent> contents, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Transaction()
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object upsertMessagesWithContent(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.model.chat.ConversationMessageWithContent[] message, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Transaction()
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object upsertMessagesWithContent(@org.jetbrains.annotations.NotNull()
    java.util.List<com.getcode.oct24.domain.model.chat.ConversationMessageWithContent> messages, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.RewriteQueriesToDropUnusedColumns()
    @androidx.room.Transaction()
    @androidx.room.Query(value = "\n        SELECT DISTINCT * FROM messages\n        LEFT JOIN message_contents ON messages.idBase58 = message_contents.messageIdBase58\n        LEFT JOIN members ON messages.senderIdBase58 = members.memberIdBase58 AND messages.conversationIdBase58 = members.conversationIdBase58\n        WHERE messages.conversationIdBase58 = :id\n        ORDER BY dateMillis DESC\n    ")
    @org.jetbrains.annotations.NotNull()
    public abstract androidx.paging.PagingSource<java.lang.Integer, com.getcode.oct24.domain.model.chat.ConversationMessageWithContentAndMember> observeConversationMessages(@org.jetbrains.annotations.NotNull()
    java.lang.String id);
    
    @org.jetbrains.annotations.NotNull()
    public abstract androidx.paging.PagingSource<java.lang.Integer, com.getcode.oct24.domain.model.chat.ConversationMessageWithContentAndMember> observeConversationMessages(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> id);
    
    @androidx.room.Query(value = "SELECT * FROM messages WHERE conversationIdBase58 = :conversationId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object queryMessages(@org.jetbrains.annotations.NotNull()
    java.lang.String conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.getcode.oct24.domain.model.chat.ConversationMessage>> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object queryMessages(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.getcode.oct24.domain.model.chat.ConversationMessage>> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM messages WHERE conversationIdBase58 = :conversationId ORDER BY dateMillis DESC LIMIT 1")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getNewestMessage(@org.jetbrains.annotations.NotNull()
    java.lang.String conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.getcode.oct24.domain.model.chat.ConversationMessage> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getNewestMessage(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.getcode.oct24.domain.model.chat.ConversationMessage> $completion);
    
    @androidx.room.Query(value = "DELETE FROM messages WHERE conversationIdBase58 = :conversationId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object removeForConversation(@org.jetbrains.annotations.NotNull()
    java.lang.String conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object removeForConversation(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "UPDATE messages SET deleted = 1 WHERE idBase58 = :messageId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object markDeleted(@org.jetbrains.annotations.NotNull()
    java.lang.String messageId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object markDeleted(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> messageId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM message_contents WHERE messageIdBase58 = :messageId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object removeContentsForMessage(@org.jetbrains.annotations.NotNull()
    java.lang.String messageId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object removeContentsForMessage(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> messageId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object markDeletedAndRemoveContents(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> messageId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM messages WHERE conversationIdBase58 NOT IN (:chatIds)")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object purgeMessagesNotInByString(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> chatIds, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object purgeMessagesNotIn(@org.jetbrains.annotations.NotNull()
    java.util.List<? extends java.util.List<java.lang.Byte>> chatIds, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM messages")
    public abstract void clearMessages();
    
    @androidx.room.Query(value = "DELETE FROM messages WHERE conversationIdBase58 = :chatId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object clearMessagesForChat(@org.jetbrains.annotations.NotNull()
    java.lang.String chatId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object clearMessagesForChat(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> chatId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object upsertMessages(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMessageDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<com.getcode.oct24.domain.model.chat.ConversationMessage> message, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object upsertMessageContent(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMessageDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> messageId, @org.jetbrains.annotations.NotNull()
        java.util.List<? extends com.getcode.model.chat.MessageContent> contents, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @androidx.room.Transaction()
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object upsertMessagesWithContent(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMessageDao $this, @org.jetbrains.annotations.NotNull()
        com.getcode.oct24.domain.model.chat.ConversationMessageWithContent[] message, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @androidx.room.Transaction()
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object upsertMessagesWithContent(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMessageDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<com.getcode.oct24.domain.model.chat.ConversationMessageWithContent> messages, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public static androidx.paging.PagingSource<java.lang.Integer, com.getcode.oct24.domain.model.chat.ConversationMessageWithContentAndMember> observeConversationMessages(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMessageDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> id) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object queryMessages(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMessageDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super java.util.List<com.getcode.oct24.domain.model.chat.ConversationMessage>> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object getNewestMessage(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMessageDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super com.getcode.oct24.domain.model.chat.ConversationMessage> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object removeForConversation(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMessageDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object markDeleted(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMessageDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> messageId, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object removeContentsForMessage(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMessageDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> messageId, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object markDeletedAndRemoveContents(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMessageDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> messageId, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object purgeMessagesNotIn(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMessageDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<? extends java.util.List<java.lang.Byte>> chatIds, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object clearMessagesForChat(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMessageDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> chatId, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
    }
}