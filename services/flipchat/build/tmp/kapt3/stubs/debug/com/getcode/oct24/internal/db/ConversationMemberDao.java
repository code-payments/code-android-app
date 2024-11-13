package com.getcode.oct24.internal.db;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0010\u0011\n\u0002\b\u0002\bg\u0018\u00002\u00020\u0001J\b\u0010\u0002\u001a\u00020\u0003H\'J$\u0010\u0004\u001a\u00020\u00032\u0006\u0010\u0005\u001a\u00020\u00062\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00060\bH\u00a7@\u00a2\u0006\u0002\u0010\tJ.\u0010\n\u001a\u00020\u00032\u0010\u0010\u0005\u001a\f\u0012\u0004\u0012\u00020\u000b0\bj\u0002`\f2\f\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000e0\bH\u0096@\u00a2\u0006\u0002\u0010\u000fJ2\u0010\u0010\u001a\u00020\u00032\u0010\u0010\u0011\u001a\f\u0012\u0004\u0012\u00020\u000b0\bj\u0002`\f2\u0010\u0010\u0005\u001a\f\u0012\u0004\u0012\u00020\u000b0\bj\u0002`\fH\u0096@\u00a2\u0006\u0002\u0010\u000fJ\u001e\u0010\u0010\u001a\u00020\u00032\u0006\u0010\u0011\u001a\u00020\u00062\u0006\u0010\u0005\u001a\u00020\u0006H\u00a7@\u00a2\u0006\u0002\u0010\u0012J \u0010\u0013\u001a\u00020\u00032\u0010\u0010\u0005\u001a\f\u0012\u0004\u0012\u00020\u000b0\bj\u0002`\fH\u0096@\u00a2\u0006\u0002\u0010\u0014J\u0016\u0010\u0013\u001a\u00020\u00032\u0006\u0010\u0005\u001a\u00020\u0006H\u00a7@\u00a2\u0006\u0002\u0010\u0015J\"\u0010\u0016\u001a\u00020\u00032\u0012\u0010\r\u001a\n\u0012\u0006\b\u0001\u0012\u00020\u000e0\u0017\"\u00020\u000eH\u00a7@\u00a2\u0006\u0002\u0010\u0018\u00a8\u0006\u0019"}, d2 = {"Lcom/getcode/oct24/internal/db/ConversationMemberDao;", "", "clearConversations", "", "purgeMembersNotInByString", "conversationId", "", "memberIds", "", "(Ljava/lang/String;Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "refreshMembers", "", "Lcom/getcode/model/ID;", "members", "Lcom/getcode/oct24/domain/model/chat/ConversationMember;", "(Ljava/util/List;Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "removeMemberFromConversation", "memberId", "(Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "removeMembersFrom", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "upsertMembers", "", "([Lcom/getcode/oct24/domain/model/chat/ConversationMember;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "flipchat_debug"})
@androidx.room.Dao()
public abstract interface ConversationMemberDao {
    
    @androidx.room.Insert(onConflict = 1)
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object upsertMembers(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.model.chat.ConversationMember[] members, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM members WHERE conversationIdBase58 = :conversationId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object removeMembersFrom(@org.jetbrains.annotations.NotNull()
    java.lang.String conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object removeMembersFrom(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM members WHERE memberIdBase58 = :memberId AND conversationIdBase58 = :conversationId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object removeMemberFromConversation(@org.jetbrains.annotations.NotNull()
    java.lang.String memberId, @org.jetbrains.annotations.NotNull()
    java.lang.String conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object removeMemberFromConversation(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> memberId, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM members WHERE memberIdBase58 NOT IN (:memberIds) AND conversationIdBase58 = :conversationId")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object purgeMembersNotInByString(@org.jetbrains.annotations.NotNull()
    java.lang.String conversationId, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.String> memberIds, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object refreshMembers(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
    java.util.List<com.getcode.oct24.domain.model.chat.ConversationMember> members, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM members")
    public abstract void clearConversations();
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object removeMembersFrom(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMemberDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object removeMemberFromConversation(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMemberDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> memberId, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public static java.lang.Object refreshMembers(@org.jetbrains.annotations.NotNull()
        com.getcode.oct24.internal.db.ConversationMemberDao $this, @org.jetbrains.annotations.NotNull()
        java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
        java.util.List<com.getcode.oct24.domain.model.chat.ConversationMember> members, @org.jetbrains.annotations.NotNull()
        kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
            return null;
        }
    }
}