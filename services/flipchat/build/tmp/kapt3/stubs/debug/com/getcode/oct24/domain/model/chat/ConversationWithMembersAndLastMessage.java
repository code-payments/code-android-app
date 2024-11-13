package com.getcode.oct24.domain.model.chat;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000N\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\b\n\u0002\u0010\b\n\u0002\b\r\b\u0086\b\u0018\u00002\u00020\u0001B%\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\b\u00a2\u0006\u0002\u0010\tJ\t\u0010%\u001a\u00020\u0003H\u00c6\u0003J\u000f\u0010&\u001a\b\u0012\u0004\u0012\u00020\u00060\u0005H\u00c6\u0003J\u000b\u0010\'\u001a\u0004\u0018\u00010\bH\u00c6\u0003J/\u0010(\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\u000e\b\u0002\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u00052\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\bH\u00c6\u0001J\u0013\u0010)\u001a\u00020\u00162\b\u0010*\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010+\u001a\u00020\"H\u00d6\u0001J\"\u0010,\u001a\b\u0012\u0004\u0012\u00020\u00060\u00052\u0014\u0010-\u001a\u0010\u0012\u0004\u0012\u00020\r\u0018\u00010\u0005j\u0004\u0018\u0001`\u000eJ\t\u0010.\u001a\u00020\u0012H\u00d6\u0001R\u0016\u0010\u0002\u001a\u00020\u00038\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u001b\u0010\f\u001a\f\u0012\u0004\u0012\u00020\r0\u0005j\u0002`\u000e8F\u00a2\u0006\u0006\u001a\u0004\b\u000f\u0010\u0010R\u0013\u0010\u0011\u001a\u0004\u0018\u00010\u00128F\u00a2\u0006\u0006\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0015\u001a\u00020\u00168F\u00a2\u0006\u0006\u001a\u0004\b\u0015\u0010\u0017R\u0013\u0010\u0018\u001a\u0004\u0018\u00010\u00198F\u00a2\u0006\u0006\u001a\u0004\b\u001a\u0010\u001bR\u0018\u0010\u0007\u001a\u0004\u0018\u00010\b8\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u001dR\u001c\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00060\u00058\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001e\u0010\u0010R\u0011\u0010\u001f\u001a\u00020\u00128F\u00a2\u0006\u0006\u001a\u0004\b \u0010\u0014R\u0011\u0010!\u001a\u00020\"8F\u00a2\u0006\u0006\u001a\u0004\b#\u0010$\u00a8\u0006/"}, d2 = {"Lcom/getcode/oct24/domain/model/chat/ConversationWithMembersAndLastMessage;", "", "conversation", "Lcom/getcode/oct24/domain/model/chat/Conversation;", "members", "", "Lcom/getcode/oct24/domain/model/chat/ConversationMember;", "lastMessage", "Lcom/getcode/oct24/domain/model/chat/ConversationMessageWithContent;", "(Lcom/getcode/oct24/domain/model/chat/Conversation;Ljava/util/List;Lcom/getcode/oct24/domain/model/chat/ConversationMessageWithContent;)V", "getConversation", "()Lcom/getcode/oct24/domain/model/chat/Conversation;", "id", "", "Lcom/getcode/model/ID;", "getId", "()Ljava/util/List;", "imageUri", "", "getImageUri", "()Ljava/lang/String;", "isMuted", "", "()Z", "lastActivity", "", "getLastActivity", "()Ljava/lang/Long;", "getLastMessage", "()Lcom/getcode/oct24/domain/model/chat/ConversationMessageWithContent;", "getMembers", "title", "getTitle", "unreadCount", "", "getUnreadCount", "()I", "component1", "component2", "component3", "copy", "equals", "other", "hashCode", "nonSelfMembers", "selfId", "toString", "flipchat_debug"})
public final class ConversationWithMembersAndLastMessage {
    @androidx.room.Embedded()
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.domain.model.chat.Conversation conversation = null;
    @androidx.room.Relation(parentColumn = "idBase58", entityColumn = "conversationIdBase58")
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.getcode.oct24.domain.model.chat.ConversationMember> members = null;
    @androidx.room.Relation(parentColumn = "idBase58", entityColumn = "conversationIdBase58", entity = com.getcode.oct24.domain.model.chat.ConversationMessage.class, projection = {"idBase58", "dateMillis", "senderIdBase58"})
    @org.jetbrains.annotations.Nullable()
    private final com.getcode.oct24.domain.model.chat.ConversationMessageWithContent lastMessage = null;
    
    public ConversationWithMembersAndLastMessage(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.model.chat.Conversation conversation, @org.jetbrains.annotations.NotNull()
    java.util.List<com.getcode.oct24.domain.model.chat.ConversationMember> members, @org.jetbrains.annotations.Nullable()
    com.getcode.oct24.domain.model.chat.ConversationMessageWithContent lastMessage) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.getcode.oct24.domain.model.chat.Conversation getConversation() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.getcode.oct24.domain.model.chat.ConversationMember> getMembers() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.getcode.oct24.domain.model.chat.ConversationMessageWithContent getLastMessage() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.Byte> getId() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getTitle() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.String getImageUri() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Long getLastActivity() {
        return null;
    }
    
    public final boolean isMuted() {
        return false;
    }
    
    public final int getUnreadCount() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.getcode.oct24.domain.model.chat.ConversationMember> nonSelfMembers(@org.jetbrains.annotations.Nullable()
    java.util.List<java.lang.Byte> selfId) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.getcode.oct24.domain.model.chat.Conversation component1() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.getcode.oct24.domain.model.chat.ConversationMember> component2() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.getcode.oct24.domain.model.chat.ConversationMessageWithContent component3() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastMessage copy(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.model.chat.Conversation conversation, @org.jetbrains.annotations.NotNull()
    java.util.List<com.getcode.oct24.domain.model.chat.ConversationMember> members, @org.jetbrains.annotations.Nullable()
    com.getcode.oct24.domain.model.chat.ConversationMessageWithContent lastMessage) {
        return null;
    }
    
    @java.lang.Override()
    public boolean equals(@org.jetbrains.annotations.Nullable()
    java.lang.Object other) {
        return false;
    }
    
    @java.lang.Override()
    public int hashCode() {
        return 0;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public java.lang.String toString() {
        return null;
    }
}