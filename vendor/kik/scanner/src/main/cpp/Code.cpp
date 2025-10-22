//
// Created by Brandon McAnsh on 10/22/25.
//

#include <jni.h>
#include "Code.h"
#include <android/log.h>
#include "scan/kikcodes.h"
#include "encoding/kikcode_encoding.h"
#include "scan/kikcode_scan.h"

#define LOG_TAG "KikCodeJNI"
#ifdef ENABLE_KIKCODE_LOGGING
#define LOG_TAG "KikCodeJNI"
    #define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#define LOGD(...) do {} while (0)
#endif
#define TOTAL_BYTE_COUNT    39
#define MAIN_BYTE_COUNT     35
#define DATA_BYTE_COUNT     22
#define PAYLOAD_BYTE_COUNT  20
#define ECC_BYTE_COUNT      13

KikCode* kikCodeDecode(const unsigned char *data) {
    return KikCode::parse(data);
}

// --- JNI IMPLEMENTATION ---
extern "C"
jbyteArray
Java_com_kik_scan_Scanner_encode(
        JNIEnv *env,
        jclass clazz,
        jbyteArray data
) {
    // 1. Get the raw byte pointer from the Java byte array (jbyteArray).
    // The JNIEnv->GetByteArrayElements function gives us direct access to the array's data.
    jbyte *input_bytes = env->GetByteArrayElements(data, nullptr);
    if (input_bytes == nullptr) {
        // Error handling: failed to get array elements
        return nullptr;
    }

    // 2. Prepare the output buffer.
    // Based on your reference code, the output size is a fixed value, MAIN_BYTE_COUNT.
    unsigned char output_buffer[MAIN_BYTE_COUNT] = {0}; // Initialize to all zeros

    // 3. Call your existing C++ encoding function.
    // We cast the jbyte* (which is signed) to unsigned char* as required by your function.
    kikCodeEncodeRemote(output_buffer, (const unsigned char *)input_bytes, 0);

    // 4. Release the input array elements.
    // This is crucial to avoid memory leaks. It tells the JVM we are done with the direct pointer.
    // The '0' mode indicates that we want to copy back any changes and free the buffer.
    env->ReleaseByteArrayElements(data, input_bytes, 0);

    // 5. Create a new Java byte array for the result.
    jbyteArray result_jbyte_array = env->NewByteArray(MAIN_BYTE_COUNT);
    if (result_jbyte_array == nullptr) {
        // Error handling: failed to allocate new Java byte array
        return nullptr;
    }

    // 6. Copy the C++ output buffer into the new Java byte array.
    // We cast the unsigned char* back to jbyte* (signed) for the JNI function.
    env->SetByteArrayRegion(result_jbyte_array, 0, MAIN_BYTE_COUNT, (const jbyte *)output_buffer);

    // 7. Return the new Java byte array.
    return result_jbyte_array;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_kik_scan_Scanner_decode( // Match the Kotlin class and function name
        JNIEnv *env,
        jclass clazz,
        jbyteArray data) {

    LOGD("decode: Starting decode process.");

    jbyte *input_bytes = env->GetByteArrayElements(data, nullptr);
    if (input_bytes == nullptr) {
        LOGD("decode: Failed to get byte array elements from input data.");
        return nullptr;
    }

    // 1. Call the MODIFIED kikCodeDecode ONCE to get the native object.
    LOGD("decode: Calling kikCodeDecode with input data.");
    KikCode* native_kik_code_ptr = kikCodeDecode((const unsigned char *) input_bytes);

    env->ReleaseByteArrayElements(data, input_bytes, JNI_ABORT);

    // 2. If parsing fails, return null. This is where the ReedSolomonException is caught.
    if (native_kik_code_ptr == nullptr) {
        LOGD("decode: kikCodeDecode returned null. Parsing failed.");
        return nullptr;
    }

    // 3. Based on the type, find the correct Kotlin subclass and its constructor.
    LOGD("decode: kikCodeDecode successful. Pointer: %p, Type: %d", native_kik_code_ptr, native_kik_code_ptr->type());
    const char *subclass_path = nullptr;
    switch (native_kik_code_ptr->type()) {
        case KikCode::Type::Username: subclass_path = "com/kik/scan/UsernameKikCode"; LOGD("decode: Type is Username."); break;
        case KikCode::Type::Remote:   subclass_path = "com/kik/scan/RemoteKikCode";   LOGD("decode: Type is Remote."); break;
        case KikCode::Type::Group:    subclass_path = "com/kik/scan/GroupKikCode";    LOGD("decode: Type is Group."); break;
        default:
            LOGD("decode: Unknown KikCode type encountered. Cleaning up and returning null.");
            delete native_kik_code_ptr; // Clean up before returning
            return nullptr;
    }

    jclass kikcode_subclass = env->FindClass(subclass_path);
    if (kikcode_subclass == nullptr) {
        LOGD("decode: Failed to find class: %s", subclass_path);
        delete native_kik_code_ptr;
        return nullptr;
    }
    LOGD("decode: Found subclass: %s", subclass_path);

    // The constructor takes the native pointer (a 'long')
    jmethodID constructor = env->GetMethodID(kikcode_subclass, "<init>", "(J)V");
    if (constructor == nullptr) {
        LOGD("decode: Failed to find constructor '<init>(J)V' for class: %s", subclass_path);
        delete native_kik_code_ptr;
        return nullptr;
    }
    LOGD("decode: Found constructor for subclass.");

    // 4. Instantiate the Kotlin object, passing the native pointer.
    LOGD("decode: Instantiating Kotlin object with native pointer.");
    jobject kotlin_kikcode_obj = env->NewObject(
            kikcode_subclass,
            constructor,
            (jlong)native_kik_code_ptr // Cast C++ pointer to a Java long
    );

    LOGD("decode: Successfully created Kotlin object. Returning.");
    return kotlin_kikcode_obj;
}


// --- JNI IMPLEMENTATION for the scan function ---
extern "C"
JNIEXPORT jobject JNICALL
Java_com_kik_scan_Scanner_scan(
        JNIEnv *env, jclass clazz, jbyteArray matrix,
        jint width, jint height, jint quality) {

    jbyte *matrix_bytes = env->GetByteArrayElements(matrix, nullptr);
    if (!matrix_bytes) return nullptr;

    unsigned char scan_output_buffer[MAIN_BYTE_COUNT] = {0};

    int scan_result = kikCodeScan(
            (const unsigned char *) matrix_bytes, (unsigned int) width, (unsigned int) height,
            (unsigned int) quality, scan_output_buffer,
            nullptr, nullptr, nullptr, nullptr);

    env->ReleaseByteArrayElements(matrix, matrix_bytes, JNI_ABORT);

    if (scan_result != 0) {
        return nullptr; // Scan failed, no code found
    }

    // --- If scan succeeded, decode the payload ---
    // Call kikCodeDecode to get the native KikCode object
    KikCode* native_kik_code_ptr = kikCodeDecode(scan_output_buffer);
    if (!native_kik_code_ptr) {
        return nullptr; // Decoding failed
    }

    // --- Construct the appropriate Java KikCode object ---
    const char *subclass_path = nullptr;
    switch (native_kik_code_ptr->type()) {
        case KikCode::Type::Username: subclass_path = "com/kik/scan/UsernameKikCode"; break;
        case KikCode::Type::Remote:   subclass_path = "com/kik/scan/RemoteKikCode";   break;
        case KikCode::Type::Group:    subclass_path = "com/kik/scan/GroupKikCode";    break;
        default:
            delete native_kik_code_ptr; // Clean up before returning
            return nullptr;
    }

    jclass kikcode_subclass = env->FindClass(subclass_path);
    if (kikcode_subclass == nullptr) {
        delete native_kik_code_ptr;
        return nullptr;
    }

    // The constructor takes the native pointer (as a 'long')
    jmethodID constructor = env->GetMethodID(kikcode_subclass, "<init>", "(J)V");
    if (constructor == nullptr) {
        delete native_kik_code_ptr;
        return nullptr;
    }

    // Instantiate the Kotlin object, passing the native pointer.
    jobject kotlin_kikcode_obj = env->NewObject(
            kikcode_subclass,
            constructor,
            (jlong)native_kik_code_ptr // Cast C++ pointer to a Java long
    );

    return kotlin_kikcode_obj;
}
