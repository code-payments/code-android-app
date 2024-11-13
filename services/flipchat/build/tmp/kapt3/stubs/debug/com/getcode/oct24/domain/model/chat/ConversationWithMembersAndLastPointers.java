package com.getcode.oct24.domain.model.chat;

@kotlinx.serialization.Serializable()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000l\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0087\b\u0018\u0000 22\u00020\u0001:\u000212By\b\u0011\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\u000e\u0010\u0006\u001a\n\u0012\u0004\u0012\u00020\b\u0018\u00010\u0007\u0012\u000e\u0010\t\u001a\n\u0012\u0004\u0012\u00020\n\u0018\u00010\u0007\u0012\u0014\u0010\u000b\u001a\u0010\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u0003\u0018\u00010\f\u0012\u001e\u0010\u000e\u001a\u001a\u0012\u000e\u0012\f\u0012\u0004\u0012\u00020\u000f0\u0007j\u0002`\u0010\u0012\u0004\u0012\u00020\u0003\u0018\u00010\f\u0012\b\u0010\u0011\u001a\u0004\u0018\u00010\u0012\u00a2\u0006\u0002\u0010\u0013B)\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\f\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u0007\u0012\f\u0010\t\u001a\b\u0012\u0004\u0012\u00020\n0\u0007\u00a2\u0006\u0002\u0010\u0014J\t\u0010 \u001a\u00020\u0005H\u00c6\u0003J\u000f\u0010!\u001a\b\u0012\u0004\u0012\u00020\b0\u0007H\u00c6\u0003J\u000f\u0010\"\u001a\b\u0012\u0004\u0012\u00020\n0\u0007H\u00c6\u0003J3\u0010#\u001a\u00020\u00002\b\b\u0002\u0010\u0004\u001a\u00020\u00052\u000e\b\u0002\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u00072\u000e\b\u0002\u0010\t\u001a\b\u0012\u0004\u0012\u00020\n0\u0007H\u00c6\u0001J\u0013\u0010$\u001a\u00020%2\b\u0010&\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\'\u001a\u00020\u0003H\u00d6\u0001J\t\u0010(\u001a\u00020\rH\u00d6\u0001J&\u0010)\u001a\u00020*2\u0006\u0010+\u001a\u00020\u00002\u0006\u0010,\u001a\u00020-2\u0006\u0010.\u001a\u00020/H\u00c1\u0001\u00a2\u0006\u0002\b0R\u0016\u0010\u0004\u001a\u00020\u00058\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016R\u001c\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u00078\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0018R,\u0010\u000e\u001a\u0018\u0012\u000e\u0012\f\u0012\u0004\u0012\u00020\u000f0\u0007j\u0002`\u0010\u0012\u0004\u0012\u00020\u00030\f8\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR\u001c\u0010\u000b\u001a\u000e\u0012\u0004\u0012\u00020\r\u0012\u0004\u0012\u00020\u00030\f8\u0002X\u0083\u0004\u00a2\u0006\u0002\n\u0000R\u001d\u0010\u001b\u001a\u000e\u0012\u0004\u0012\u00020\u001c\u0012\u0004\u0012\u00020\u001d0\f8F\u00a2\u0006\u0006\u001a\u0004\b\u001e\u0010\u001aR\u001c\u0010\t\u001a\b\u0012\u0004\u0012\u00020\n0\u00078\u0006X\u0087\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010\u0018\u00a8\u00063"}, d2 = {"Lcom/getcode/oct24/domain/model/chat/ConversationWithMembersAndLastPointers;", "", "seen1", "", "conversation", "Lcom/getcode/oct24/domain/model/chat/Conversation;", "members", "", "Lcom/getcode/oct24/domain/model/chat/ConversationMember;", "pointersCrossRef", "Lcom/getcode/oct24/domain/model/chat/ConversationPointerCrossRef;", "nameCounts", "", "", "membersUnique", "", "Lcom/getcode/model/ID;", "serializationConstructorMarker", "Lkotlinx/serialization/internal/SerializationConstructorMarker;", "(ILcom/getcode/oct24/domain/model/chat/Conversation;Ljava/util/List;Ljava/util/List;Ljava/util/Map;Ljava/util/Map;Lkotlinx/serialization/internal/SerializationConstructorMarker;)V", "(Lcom/getcode/oct24/domain/model/chat/Conversation;Ljava/util/List;Ljava/util/List;)V", "getConversation", "()Lcom/getcode/oct24/domain/model/chat/Conversation;", "getMembers", "()Ljava/util/List;", "getMembersUnique", "()Ljava/util/Map;", "pointers", "Ljava/util/UUID;", "Lcom/getcode/model/chat/MessageStatus;", "getPointers", "getPointersCrossRef", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "toString", "write$Self", "", "self", "output", "Lkotlinx/serialization/encoding/CompositeEncoder;", "serialDesc", "Lkotlinx/serialization/descriptors/SerialDescriptor;", "write$Self$flipchat_debug", "$serializer", "Companion", "flipchat_debug"})
public final class ConversationWithMembersAndLastPointers {
    @androidx.room.Embedded()
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.domain.model.chat.Conversation conversation = null;
    @androidx.room.Relation(parentColumn = "idBase58", entityColumn = "conversationIdBase58")
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.getcode.oct24.domain.model.chat.ConversationMember> members = null;
    @androidx.room.Relation(parentColumn = "idBase58", entityColumn = "conversationIdBase58", entity = com.getcode.oct24.domain.model.chat.ConversationPointerCrossRef.class)
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.getcode.oct24.domain.model.chat.ConversationPointerCrossRef> pointersCrossRef = null;
    @androidx.room.Ignore()
    @org.jetbrains.annotations.NotNull()
    private final java.util.Map<java.lang.String, java.lang.Integer> nameCounts = null;
    @androidx.room.Ignore()
    @org.jetbrains.annotations.NotNull()
    private final java.util.Map<java.util.List<java.lang.Byte>, java.lang.Integer> membersUnique = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers.Companion Companion = null;
    
    public ConversationWithMembersAndLastPointers(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.model.chat.Conversation conversation, @org.jetbrains.annotations.NotNull()
    java.util.List<com.getcode.oct24.domain.model.chat.ConversationMember> members, @org.jetbrains.annotations.NotNull()
    java.util.List<com.getcode.oct24.domain.model.chat.ConversationPointerCrossRef> pointersCrossRef) {
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
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.getcode.oct24.domain.model.chat.ConversationPointerCrossRef> getPointersCrossRef() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.Map<java.util.List<java.lang.Byte>, java.lang.Integer> getMembersUnique() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.Map<java.util.UUID, com.getcode.model.chat.MessageStatus> getPointers() {
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
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.getcode.oct24.domain.model.chat.ConversationPointerCrossRef> component3() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers copy(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.model.chat.Conversation conversation, @org.jetbrains.annotations.NotNull()
    java.util.List<com.getcode.oct24.domain.model.chat.ConversationMember> members, @org.jetbrains.annotations.NotNull()
    java.util.List<com.getcode.oct24.domain.model.chat.ConversationPointerCrossRef> pointersCrossRef) {
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
    
    @kotlin.jvm.JvmStatic()
    public static final void write$Self$flipchat_debug(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers self, @org.jetbrains.annotations.NotNull()
    kotlinx.serialization.encoding.CompositeEncoder output, @org.jetbrains.annotations.NotNull()
    kotlinx.serialization.descriptors.SerialDescriptor serialDesc) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00006\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0011\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c7\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0003J\u0018\u0010\b\u001a\f\u0012\b\u0012\u0006\u0012\u0002\b\u00030\n0\tH\u00d6\u0001\u00a2\u0006\u0002\u0010\u000bJ\u0011\u0010\f\u001a\u00020\u00022\u0006\u0010\r\u001a\u00020\u000eH\u00d6\u0001J\u0019\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0002H\u00d6\u0001R\u0014\u0010\u0004\u001a\u00020\u00058VX\u00d6\u0005\u00a2\u0006\u0006\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\u0014"}, d2 = {"com/getcode/oct24/domain/model/chat/ConversationWithMembersAndLastPointers.$serializer", "Lkotlinx/serialization/internal/GeneratedSerializer;", "Lcom/getcode/oct24/domain/model/chat/ConversationWithMembersAndLastPointers;", "()V", "descriptor", "Lkotlinx/serialization/descriptors/SerialDescriptor;", "getDescriptor", "()Lkotlinx/serialization/descriptors/SerialDescriptor;", "childSerializers", "", "Lkotlinx/serialization/KSerializer;", "()[Lkotlinx/serialization/KSerializer;", "deserialize", "decoder", "Lkotlinx/serialization/encoding/Decoder;", "serialize", "", "encoder", "Lkotlinx/serialization/encoding/Encoder;", "value", "flipchat_debug"})
    @java.lang.Deprecated()
    public static final class $serializer implements kotlinx.serialization.internal.GeneratedSerializer<com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers> {
        @org.jetbrains.annotations.NotNull()
        public static final com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers.$serializer INSTANCE = null;
        
        private $serializer() {
            super();
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public kotlinx.serialization.KSerializer<?>[] childSerializers() {
            return null;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers deserialize(@org.jetbrains.annotations.NotNull()
        kotlinx.serialization.encoding.Decoder decoder) {
            return null;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public kotlinx.serialization.descriptors.SerialDescriptor getDescriptor() {
            return null;
        }
        
        @java.lang.Override()
        public void serialize(@org.jetbrains.annotations.NotNull()
        kotlinx.serialization.encoding.Encoder encoder, @org.jetbrains.annotations.NotNull()
        com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers value) {
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public kotlinx.serialization.KSerializer<?>[] typeParametersSerializers() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000f\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004H\u00c6\u0001\u00a8\u0006\u0006"}, d2 = {"Lcom/getcode/oct24/domain/model/chat/ConversationWithMembersAndLastPointers$Companion;", "", "()V", "serializer", "Lkotlinx/serialization/KSerializer;", "Lcom/getcode/oct24/domain/model/chat/ConversationWithMembersAndLastPointers;", "flipchat_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final kotlinx.serialization.KSerializer<com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers> serializer() {
            return null;
        }
    }
}