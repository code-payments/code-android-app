// Debug-only JVMTI agent: redefines classes from dex bytes. Loaded with
// android.os.Debug.attachJvmtiAgent, so Agent_OnAttach runs in the app process.
// Build: scripts/hotswap/build-agent.sh
#include <jni.h>
#include <jvmti.h>
#include <string.h>
#include <android/log.h>

#define TAG "Hotswap"
static jvmtiEnv *g_jvmti = NULL;
static jint g_attach_error = -1;

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *vm, char *options, void *reserved) {
    jint r = (*vm)->GetEnv(vm, (void **)&g_jvmti, JVMTI_VERSION_1_2);
    if (r != JNI_OK || g_jvmti == NULL) {
        g_attach_error = 1000 + r;
        return r;
    }
    jvmtiCapabilities caps;
    memset(&caps, 0, sizeof(caps));
    caps.can_redefine_classes = 1;
    jvmtiError e = (*g_jvmti)->AddCapabilities(g_jvmti, &caps);
    g_attach_error = (jint)e;
    __android_log_print(ANDROID_LOG_INFO, TAG, "Agent_OnAttach AddCapabilities=%d", e);
    return JNI_OK;
}

// 0 = ready, otherwise the jvmtiError (or 1000+GetEnv result) from attach; -1 = never attached.
JNIEXPORT jint JNICALL Java_com_flipcash_app_hotswap_JvmtiAgent_attachStatus(JNIEnv *env, jclass c) {
    return g_attach_error;
}

// Redefines classes[i] with dexes[i] (a dex file holding exactly that class). Returns a jvmtiError.
JNIEXPORT jint JNICALL Java_com_flipcash_app_hotswap_JvmtiAgent_redefine(
        JNIEnv *env, jclass c, jobjectArray classes, jobjectArray dexes) {
    if (g_jvmti == NULL) return 1999;
    jint n = (*env)->GetArrayLength(env, classes);
    jvmtiClassDefinition defs[64];
    jbyte *bufs[64];
    jbyteArray arrs[64];
    if (n > 64) return 1998;
    for (jint i = 0; i < n; i++) {
        arrs[i] = (jbyteArray)(*env)->GetObjectArrayElement(env, dexes, i);
        defs[i].klass = (jclass)(*env)->GetObjectArrayElement(env, classes, i);
        defs[i].class_byte_count = (*env)->GetArrayLength(env, arrs[i]);
        bufs[i] = (*env)->GetByteArrayElements(env, arrs[i], NULL);
        defs[i].class_bytes = (const unsigned char *)bufs[i];
    }
    jvmtiError e = (*g_jvmti)->RedefineClasses(g_jvmti, n, defs);
    for (jint i = 0; i < n; i++) (*env)->ReleaseByteArrayElements(env, arrs[i], bufs[i], JNI_ABORT);
    __android_log_print(ANDROID_LOG_INFO, TAG, "RedefineClasses(%d)=%d", n, e);
    return (jint)e;
}
