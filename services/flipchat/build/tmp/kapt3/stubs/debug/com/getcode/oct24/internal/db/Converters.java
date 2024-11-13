package com.getcode.oct24.internal.db;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c0\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u0003\u001a\u00020\u00042\f\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006H\u0007J\u0016\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u0006\u0010\t\u001a\u00020\u0004H\u0007\u00a8\u0006\n"}, d2 = {"Lcom/getcode/oct24/internal/db/Converters;", "", "()V", "membersToString", "", "members", "", "Lcom/getcode/oct24/data/Member;", "stringToMembers", "value", "flipchat_debug"})
public final class Converters {
    @org.jetbrains.annotations.NotNull()
    public static final com.getcode.oct24.internal.db.Converters INSTANCE = null;
    
    private Converters() {
        super();
    }
    
    @androidx.room.TypeConverter()
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String membersToString(@org.jetbrains.annotations.NotNull()
    java.util.List<com.getcode.oct24.data.Member> members) {
        return null;
    }
    
    @androidx.room.TypeConverter()
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<com.getcode.oct24.data.Member> stringToMembers(@org.jetbrains.annotations.NotNull()
    java.lang.String value) {
        return null;
    }
}