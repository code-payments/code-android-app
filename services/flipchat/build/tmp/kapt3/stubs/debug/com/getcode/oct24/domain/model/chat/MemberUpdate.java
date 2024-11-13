package com.getcode.oct24.domain.model.chat;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0010\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\bv\u0018\u00002\u00020\u0001:\u0001\u0002\u0082\u0001\u0001\u0003\u00a8\u0006\u0004"}, d2 = {"Lcom/getcode/oct24/domain/model/chat/MemberUpdate;", "", "Refresh", "Lcom/getcode/oct24/domain/model/chat/MemberUpdate$Refresh;", "flipchat_debug"})
public abstract interface MemberUpdate {
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0013\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\u0002\u0010\u0005J\u000f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00c6\u0003J\u0019\u0010\t\u001a\u00020\u00002\u000e\b\u0002\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00c6\u0001J\u0013\u0010\n\u001a\u00020\u000b2\b\u0010\f\u001a\u0004\u0018\u00010\rH\u00d6\u0003J\t\u0010\u000e\u001a\u00020\u000fH\u00d6\u0001J\t\u0010\u0010\u001a\u00020\u0011H\u00d6\u0001R\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\u0012"}, d2 = {"Lcom/getcode/oct24/domain/model/chat/MemberUpdate$Refresh;", "Lcom/getcode/oct24/domain/model/chat/MemberUpdate;", "members", "", "Lcom/getcode/oct24/data/Member;", "(Ljava/util/List;)V", "getMembers", "()Ljava/util/List;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
    public static final class Refresh implements com.getcode.oct24.domain.model.chat.MemberUpdate {
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<com.getcode.oct24.data.Member> members = null;
        
        public Refresh(@org.jetbrains.annotations.NotNull()
        java.util.List<com.getcode.oct24.data.Member> members) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<com.getcode.oct24.data.Member> getMembers() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<com.getcode.oct24.data.Member> component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.getcode.oct24.domain.model.chat.MemberUpdate.Refresh copy(@org.jetbrains.annotations.NotNull()
        java.util.List<com.getcode.oct24.data.Member> members) {
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
}