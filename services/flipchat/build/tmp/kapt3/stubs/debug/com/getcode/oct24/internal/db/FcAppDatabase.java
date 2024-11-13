package com.getcode.oct24.internal.db;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000P\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\b!\u0018\u0000 \u00192\u00020\u00012\u00020\u0002:\u0003\u0019\u001a\u001bB\u0005\u00a2\u0006\u0002\u0010\u0003J\b\u0010\u0004\u001a\u00020\u0005H\u0016J\b\u0010\u0006\u001a\u00020\u0007H&J\b\u0010\b\u001a\u00020\tH&J\b\u0010\n\u001a\u00020\u000bH&J\b\u0010\f\u001a\u00020\rH&J\u0010\u0010\u000e\u001a\u00020\u00052\u0006\u0010\u000f\u001a\u00020\u0010H\u0016J\b\u0010\u0011\u001a\u00020\u0012H&J\b\u0010\u0013\u001a\u00020\u0014H&J\b\u0010\u0015\u001a\u00020\u0016H&J\b\u0010\u0017\u001a\u00020\u0018H&\u00a8\u0006\u001c"}, d2 = {"Lcom/getcode/oct24/internal/db/FcAppDatabase;", "Landroidx/room/RoomDatabase;", "Lcom/getcode/services/db/ClosableDatabase;", "()V", "closeDb", "", "conversationDao", "Lcom/getcode/oct24/internal/db/ConversationDao;", "conversationMembersDao", "Lcom/getcode/oct24/internal/db/ConversationMemberDao;", "conversationMessageDao", "Lcom/getcode/oct24/internal/db/ConversationMessageDao;", "conversationPointersDao", "Lcom/getcode/oct24/internal/db/ConversationPointerDao;", "deleteDb", "context", "Landroid/content/Context;", "prefBoolDao", "Lcom/getcode/oct24/internal/db/PrefBoolDao;", "prefDoubleDao", "Lcom/getcode/oct24/internal/db/PrefDoubleDao;", "prefIntDao", "Lcom/getcode/oct24/internal/db/PrefIntDao;", "prefStringDao", "Lcom/getcode/oct24/internal/db/PrefStringDao;", "Companion", "Migration6To7", "Migration7To8", "flipchat_debug"})
@androidx.room.Database(entities = {com.getcode.services.model.PrefInt.class, com.getcode.services.model.PrefString.class, com.getcode.services.model.PrefBool.class, com.getcode.services.model.PrefDouble.class, com.getcode.oct24.domain.model.chat.Conversation.class, com.getcode.oct24.domain.model.chat.ConversationMember.class, com.getcode.oct24.domain.model.chat.ConversationPointerCrossRef.class, com.getcode.oct24.domain.model.chat.ConversationMessage.class, com.getcode.oct24.domain.model.chat.ConversationMessageContent.class}, autoMigrations = {@androidx.room.AutoMigration(from = 1, to = 2), @androidx.room.AutoMigration(from = 2, to = 3), @androidx.room.AutoMigration(from = 3, to = 4), @androidx.room.AutoMigration(from = 4, to = 5), @androidx.room.AutoMigration(from = 5, to = 6), @androidx.room.AutoMigration(from = 6, to = 7, spec = com.getcode.oct24.internal.db.FcAppDatabase.Migration6To7.class), @androidx.room.AutoMigration(from = 7, to = 8, spec = com.getcode.oct24.internal.db.FcAppDatabase.Migration7To8.class)}, version = 8)
@androidx.room.TypeConverters(value = {com.getcode.services.db.SharedConverters.class, com.getcode.oct24.internal.db.Converters.class})
public abstract class FcAppDatabase extends androidx.room.RoomDatabase implements com.getcode.services.db.ClosableDatabase {
    @org.jetbrains.annotations.Nullable()
    private static com.getcode.oct24.internal.db.FcAppDatabase instance;
    @org.jetbrains.annotations.NotNull()
    private static io.reactivex.rxjava3.subjects.BehaviorSubject<java.lang.Boolean> isInitSubject;
    @org.jetbrains.annotations.NotNull()
    private static io.reactivex.rxjava3.core.Flowable<java.lang.Boolean> isInit;
    @org.jetbrains.annotations.NotNull()
    private static java.lang.String dbName = "";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String dbNamePrefix = "FcAppDatabase";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String dbNameSuffix = ".db";
    @org.jetbrains.annotations.NotNull()
    public static final com.getcode.oct24.internal.db.FcAppDatabase.Companion Companion = null;
    
    public FcAppDatabase() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.getcode.oct24.internal.db.PrefIntDao prefIntDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.getcode.oct24.internal.db.PrefStringDao prefStringDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.getcode.oct24.internal.db.PrefBoolDao prefBoolDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.getcode.oct24.internal.db.PrefDoubleDao prefDoubleDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.getcode.oct24.internal.db.ConversationDao conversationDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.getcode.oct24.internal.db.ConversationPointerDao conversationPointersDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.getcode.oct24.internal.db.ConversationMessageDao conversationMessageDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.getcode.oct24.internal.db.ConversationMemberDao conversationMembersDao();
    
    @java.lang.Override()
    public void closeDb() {
    }
    
    @java.lang.Override()
    public void deleteDb(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000B\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0013\u001a\u0004\u0018\u00010\bJ\u0016\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u0004J\u0006\u0010\u0019\u001a\u00020\u000bJ\u0006\u0010\u001a\u001a\u00020\bR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R-\u0010\t\u001a\u0015\u0012\f\u0012\n \f*\u0004\u0018\u00010\u000b0\u000b0\n\u00a2\u0006\u0002\b\rX\u0086\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\t\u0010\u000e\"\u0004\b\u000f\u0010\u0010R\u0014\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u000b0\u0012X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001b"}, d2 = {"Lcom/getcode/oct24/internal/db/FcAppDatabase$Companion;", "", "()V", "dbName", "", "dbNamePrefix", "dbNameSuffix", "instance", "Lcom/getcode/oct24/internal/db/FcAppDatabase;", "isInit", "Lio/reactivex/rxjava3/core/Flowable;", "", "kotlin.jvm.PlatformType", "Lio/reactivex/rxjava3/annotations/NonNull;", "()Lio/reactivex/rxjava3/core/Flowable;", "setInit", "(Lio/reactivex/rxjava3/core/Flowable;)V", "isInitSubject", "Lio/reactivex/rxjava3/subjects/BehaviorSubject;", "getInstance", "init", "", "context", "Landroid/content/Context;", "entropyB64", "isOpen", "requireInstance", "flipchat_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final io.reactivex.rxjava3.core.Flowable<java.lang.Boolean> isInit() {
            return null;
        }
        
        public final void setInit(@org.jetbrains.annotations.NotNull()
        io.reactivex.rxjava3.core.Flowable<java.lang.Boolean> p0) {
        }
        
        public final boolean isOpen() {
            return false;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final com.getcode.oct24.internal.db.FcAppDatabase getInstance() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.getcode.oct24.internal.db.FcAppDatabase requireInstance() {
            return null;
        }
        
        public final void init(@org.jetbrains.annotations.NotNull()
        android.content.Context context, @org.jetbrains.annotations.NotNull()
        java.lang.String entropyB64) {
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u00012\u00020\u0002B\u0005\u00a2\u0006\u0002\u0010\u0003J\u0010\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u0007H\u0016\u00a8\u0006\b"}, d2 = {"Lcom/getcode/oct24/internal/db/FcAppDatabase$Migration6To7;", "Landroidx/room/migration/Migration;", "Landroidx/room/migration/AutoMigrationSpec;", "()V", "migrate", "", "db", "Landroidx/sqlite/db/SupportSQLiteDatabase;", "flipchat_debug"})
    public static final class Migration6To7 extends androidx.room.migration.Migration implements androidx.room.migration.AutoMigrationSpec {
        
        public Migration6To7() {
            super(0, 0);
        }
        
        @java.lang.Override()
        public void migrate(@org.jetbrains.annotations.NotNull()
        androidx.sqlite.db.SupportSQLiteDatabase db) {
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u00012\u00020\u0002B\u0005\u00a2\u0006\u0002\u0010\u0003J\u0010\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u0007H\u0016\u00a8\u0006\b"}, d2 = {"Lcom/getcode/oct24/internal/db/FcAppDatabase$Migration7To8;", "Landroidx/room/migration/Migration;", "Landroidx/room/migration/AutoMigrationSpec;", "()V", "migrate", "", "db", "Landroidx/sqlite/db/SupportSQLiteDatabase;", "flipchat_debug"})
    public static final class Migration7To8 extends androidx.room.migration.Migration implements androidx.room.migration.AutoMigrationSpec {
        
        public Migration7To8() {
            super(0, 0);
        }
        
        @java.lang.Override()
        public void migrate(@org.jetbrains.annotations.NotNull()
        androidx.sqlite.db.SupportSQLiteDatabase db) {
        }
    }
}