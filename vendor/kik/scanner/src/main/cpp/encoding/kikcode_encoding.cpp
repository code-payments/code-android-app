#include <iomanip>
#include <stdexcept>
#include <cstring>
#include <utility>
#include <android/log.h>
#include <jni.h>

#include "kikcode_encoding.h"

#include "../zxing/common/reedsolomon/ReedSolomonEncoder.h"
#include "../zxing/common/reedsolomon/ReedSolomonDecoder.h"
#include "../zxing/common/reedsolomon/ReedSolomonException.h"
#include "../zxing/common/IllegalArgumentException.h"
#include "../zxing/IllegalStateException.h"
#include "../zxing/common/Array.h"

using namespace std;
using namespace zxing;

#ifdef ENABLE_KIKCODE_LOGGING
    #define LOG_TAG "KikCodeJNI"
    #define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
    #define LOGD(...) do {} while (0)
#endif

size_t KikCode::writeByte(unsigned char *out_data, size_t offset, unsigned char value)
{
    out_data[offset] = (uint8_t)(value & 0xff);

    return offset + 1;
}

size_t KikCode::writeShort(unsigned char *out_data, size_t offset, unsigned short value)
{
    out_data[offset]     = (uint8_t)(value & 0xff);
    out_data[offset + 1] = (uint8_t)((value & 0xff00) >> 8);

    return offset + 2;
}

size_t KikCode::writeInt(unsigned char *out_data, size_t offset, unsigned int value)
{
    out_data[offset]     = (uint8_t)(value & 0xff);
    out_data[offset + 1] = (uint8_t)((value & 0xff00) >> 8);
    out_data[offset + 2] = (uint8_t)((value & 0xff0000) >> 16);
    out_data[offset + 3] = (uint8_t)((value & 0xff000000) >> 24);

    return offset + 4;
}

size_t KikCode::writeLong(unsigned char *out_data, size_t offset, unsigned long long value)
{
    uint32_t high_dword = (uint32_t)(value >> 32);

    out_data[offset]     = (uint8_t)(value & 0xff);
    out_data[offset + 1] = (uint8_t)((value & 0xff00) >> 8);
    out_data[offset + 2] = (uint8_t)((value & 0xff0000) >> 16);
    out_data[offset + 3] = (uint8_t)((value & 0xff000000) >> 24);
    out_data[offset + 4] = (uint8_t)(high_dword & 0xff);
    out_data[offset + 5] = (uint8_t)((high_dword & 0xff00) >> 8);
    out_data[offset + 6] = (uint8_t)((high_dword & 0xff0000) >> 16);
    out_data[offset + 7] = (uint8_t)((high_dword & 0xff000000) >> 24);

    return offset + 8;
}

unsigned char KikCode::readByte(unsigned char *data, size_t offset)
{
    return data[offset];
}

unsigned short KikCode::readShort(unsigned char *data, size_t offset)
{
    return data[offset] | (data[offset + 1] << 8);
}

unsigned int KikCode::readInt(unsigned char *data, size_t offset)
{
    return data[offset] | (data[offset + 1] << 8) | (data[offset + 2] << 16) | (data[offset + 3] << 24);
}

unsigned long long KikCode::readLong(unsigned char *data, size_t offset)
{
    unsigned int low = data[offset] | (data[offset + 1] << 8) | (data[offset + 2] << 16) | (data[offset + 3] << 24);
    unsigned int high = data[offset + 4] | (data[offset + 5] << 8) | (data[offset + 6] << 16) | (data[offset + 7] << 24);

    return (unsigned long long)low | ((unsigned long long)high << 32);
}

void KikCode::decode(uint8_t *data)
{
    // type
    type_ = (KikCode::Type)(data[0] & 0x1f);

    // colour code
    int colour = (data[0] & 0xe0) >> 5;
    colour |= (data[1] & 0x07) << 3;

    colour_ = (KikCode::Colour)colour;

    // extra
    extra_ = (data[1] & 0xf8) >> 3;
}

KikCode::KikCode(KikCode::Type type, KikCode::Colour colour)
: type_(type)
, colour_(colour)
, extra_(0)
{
}

KikCode::~KikCode()
{
}

KikCode::Colour KikCode::colour() const
{
    return colour_;
}

KikCode::Type KikCode::type() const
{
    return type_;
}

uint8_t KikCode::extra() const
{
    return extra_;
}

KikCode *KikCode::parse(const uint8_t *data)
{
    LOGD("parse: input data (%d bytes)", KIK_CODE_ALL_BYTE_COUNT);

    uint8_t data_section[KIK_CODE_ALL_BYTE_COUNT];

    ArrayRef<int> codeword_ints(KIK_CODE_ALL_BYTE_COUNT);

    unsigned char reordered_data[KIK_CODE_ALL_BYTE_COUNT];

    // put the ECC back on the end
    for (size_t i = 0; i < KIK_CODE_ECC_BYTE_COUNT; ++i) {
        reordered_data[i + KIK_CODE_DATA_BYTE_COUNT] = data[i];
    }

    for (size_t i = KIK_CODE_ECC_BYTE_COUNT; i < KIK_CODE_ALL_BYTE_COUNT; ++i) {
        reordered_data[i - KIK_CODE_ECC_BYTE_COUNT] = data[i];
    }

    LOGD("parse: Reordered data for RS decoder");

    for (size_t i = 0; i < KIK_CODE_ALL_BYTE_COUNT; ++i) {
        codeword_ints[i] = reordered_data[i] & 0xff;
    }

    ReedSolomonDecoder rs_decoder(GenericGF::QR_CODE_FIELD_256);

    try {
        rs_decoder.decode(codeword_ints, KIK_CODE_ECC_BYTE_COUNT-1);
    }
    catch (ReedSolomonException const &e) {
        (void)e;
        LOGD("parse: ReedSolomonException: %s", e.what());
        return nullptr;
    }
    catch (IllegalArgumentException const &e) {
        (void)e;
        LOGD("parse: IllegalArgumentException: %s", e.what());
        return nullptr;
    }
    catch (IllegalStateException const &e) {
        (void)e;
        LOGD("parse: IllegalStateException: %s", e.what());
        return nullptr;
    }

    for (size_t i = 0; i < KIK_CODE_ALL_BYTE_COUNT; ++i) {
        data_section[i] = (uint8_t)codeword_ints[i];
    }

    // type
    uint8_t type = data_section[0] & 0x1f; // lower 5 bits (bits 0-4)

    // colour code
    uint8_t colour_code = (data_section[0] & 0xe0) >> 5; // upper 3 bits of byte 0 (bits 5-7)
    colour_code |= (data_section[1] & 0x07) << 3; // lower 3 bits of byte 1 (bits 0-2)

    LOGD("parse: Decoded data section (%d bytes)", KIK_CODE_DATA_BYTE_COUNT);
    LOGD("parse: Type: %d", (int)type);
    LOGD("parse: Colour code: %d", (int)colour_code);

    KikCode *kik_code_result = nullptr;

    // The colour code in the data stream is different from the colour enum.
    // The enum has kik green as 0 but in the data stream it's 34.
    // Let's adjust it. The logic seems to be (colour_code - 34) % 64.
    // But the constructors take the enum value. The mapping is done in the app side.
    // For now we'll just cast.
    switch (type) {
    case 1:
        kik_code_result = new UsernameKikCode((Colour)colour_code);
        break;
    case 2:
        kik_code_result = new RemoteKikCode((Colour)colour_code);
        break;
    case 3:
        kik_code_result = new GroupKikCode((Colour)colour_code);
        break;
    }

    if (kik_code_result) {
        kik_code_result->decode(data_section);
    }

    if (kik_code_result) {
        LOGD("parse: Successfully parsed KikCode of type %d and colour %d.", (int)type, (int)colour_code);
    } else {
        LOGD("parse: Failed to parse KikCode (unknown type or decode error).");
    }

    return kik_code_result;
}

void KikCode::encode(uint8_t *out_data)
{
    // type
    out_data[0] = (uint32_t)type() & 0x1f; // lower 5 bits

    // colour code
    out_data[0] |= ((uint32_t)colour() << 5) & 0xe0; // upper 3 bits
    out_data[1]  = ((uint32_t)colour() >> 3) & 0x07; // lower 3 bits

    // extra
    out_data[1] |= ((uint32_t)extra() << 3) & 0xf8; // upper 5 bits

    ArrayRef<int> codeword_ints(KIK_CODE_ALL_BYTE_COUNT);

    for (size_t i = 0; i < KIK_CODE_DATA_BYTE_COUNT; ++i) {
        codeword_ints[i] = out_data[i] & 0xff;
    }

    // apply error correction
    ReedSolomonEncoder rs_encoder(*GenericGF::QR_CODE_FIELD_256);

    try {
        rs_encoder.encode(codeword_ints, KIK_CODE_ECC_BYTE_COUNT - 1);
    }
    catch (ReedSolomonException const &ignored) {
        (void)ignored;
        throw invalid_argument("Error correction error");
    }
    catch (IllegalArgumentException const &ignored) {
        (void)ignored;
        throw invalid_argument("Error correction error");
    }
    catch (IllegalStateException const &ignored) {
        (void)ignored;
        throw invalid_argument("Error correction error");
    }

    // move the ECC to the front of the data
    for (size_t i = 0; i < KIK_CODE_ECC_BYTE_COUNT; ++i) {
        out_data[i] = (uint8_t)codeword_ints[i + KIK_CODE_DATA_BYTE_COUNT];
    }

    for (size_t i = 0; i < KIK_CODE_DATA_BYTE_COUNT; ++i) {
        out_data[i + KIK_CODE_ECC_BYTE_COUNT] = (uint8_t)codeword_ints[i];
    }
}

UsernameKikCode::UsernameKikCode(Colour colour)
: KikCode(KikCode::Type::Username, colour)
{
}

void UsernameKikCode::decode(uint8_t *data_section)
{
    KikCode::decode(data_section);

    char username[32];
    size_t username_length = extra() + 2;

    if (username_length > 24) {
        throw invalid_argument("Username too long");
    }

    // See https://github.com/kikinteractive/kik-product/wiki/Scan-Code#username
    // for codepoints and 6-bit encoding
    for (size_t i = 0; i < username_length; ++i) {
        // offset is computed as the position in the 6-bit encoded input bytes
        // based on the position in the 8-bit destination

        // the username starts 2 bytes from the front of the data section so always add 2.
        // For every character in the destination username, we are effectively advancing 3/4
        // positions in the input bytes, we can't do this because you can't move to intermediate
        // bit positions so we find the start of the nearest 3-byte sequence and then use (i % 4)
        // to determine which position in the 6-bit encoding we are looking at currently
        int offset = (i - (i % 4)) * 3 / 4 + 2;
        int codepoint = 0;
        char ch = '\0';

        // every 4 characters in the username maps to a sequence of 3 packed bytes
        // in the 6-bit encoding
        //     0 1 2 3 4 5 6 7  0 1 2 3 4 5 6 7  0 1 2 3 4 5 6 7
        // 0 - 1 1 1 1 1 1 0 0  0 0 0 0 0 0 0 0  0 0 0 0 0 0 0 0 = 0x3f
        // 1 - 0 0 0 0 0 0 1 1  1 1 1 1 0 0 0 0  0 0 0 0 0 0 0 0 = 0xc0 >> 6 | 0x0f << 2
        // 2 - 0 0 0 0 0 0 0 0  0 0 0 0 1 1 1 1  1 1 0 0 0 0 0 0 = 0xf0 >> 4 | 0x03 << 4
        // 3 - 0 0 0 0 0 0 0 0  0 0 0 0 0 0 0 0  0 0 1 1 1 1 1 1 = 0xfc >> 2

        switch (i % 4) {
        case 0:
            codepoint = (data_section[offset] & 0x3f);
            break;
        case 1:
            codepoint = ((data_section[offset] & 0xc0) >> 6) | ((data_section[offset + 1] & 0x0f) << 2);
            break;
        case 2:
            codepoint = ((data_section[offset + 1] & 0xf0) >> 4) | ((data_section[offset + 2] & 0x03) << 4);
            break;
        case 3:
            codepoint = (data_section[offset + 2] & 0xfc) >> 2;
            break;
        }

        if (codepoint < 26) {
            ch = 'A' + codepoint;
        }
        else if (codepoint < 52) {
            ch = 'a' + (codepoint - 26);
        }
        else if (codepoint < 62) {
            ch = '0' + (codepoint - 52);
        }
        else if (codepoint == 62) {
            ch = '.';
        }
        else if (codepoint == 63) {
            ch = '_';
        }
        else {
            throw invalid_argument("Unknown codepoint");
        }

        username[i] = ch;
    }

    // null-terminate the username
    username[username_length] = '\0';

    username_ = std::string(username, username_length);
    nonce_ = data_section[20] | ((int)data_section[21] << 8);
}

UsernameKikCode::UsernameKikCode(std::string username, uint16_t nonce, Colour colour)
: KikCode(KikCode::Type::Username, colour)
, username_(std::move(username))
, nonce_(nonce)
{
}

void UsernameKikCode::encode(uint8_t *out_data)
{
    memset(out_data, 0, KIK_CODE_DATA_BYTE_COUNT);

    extra_ = username_.length() - 2;

    // encode 6-bit username section
    size_t i = 0;
    if (username_.length() < 2) {
        throw invalid_argument("Username too short");
    }
    else if (username_.length() > 24) {
        throw invalid_argument("Username too long");
    }

    int offset = 0;

    // See UsernameKikCode::decode for encoding reference
    for (size_t l = username_.length(); i < l; ++i) {
        offset = (i - (i % 4)) * 3 / 4 + 2;

        char ch = username_[i];
        int value = 0;

        if (ch >= 'A' && ch <= 'Z') {
            value = (ch - 'A');
        }
        else if (ch >= 'a' && ch <= 'z') {
            value = (ch - 'a') + 26;
        }
        else if (ch >= '0' && ch <= '9') {
            value = (ch - '0') + 52;
        }
        else if (ch == '.') {
            value = 62;
        }
        else if (ch == '_') {
            value = 63;
        }
        else {
            throw invalid_argument("Invalid character");
            return;
        }

        switch (i % 4) {
        case 0:
            out_data[offset] = (value & 0x3f);
            break;
        case 1:
            out_data[offset]  |= (value & 0x03) << 6;
            out_data[offset+1] = (value & 0x3c) >> 2;
            break;
        case 2:
            out_data[offset+1] |= (value & 0x0f) << 4;
            out_data[offset+2]  = (value & 0x30) >> 4;
            break;
        case 3:
            out_data[offset+2] |= (value & 0x3f) << 2;
            break;
        }
    }

    for (offset += 3; offset < 20; ++offset) {
        out_data[offset] = 0xaa ^ offset;
    }

    writeShort(out_data, 20, nonce_);

    // finish the encoding and write the error correction
    KikCode::encode(out_data);
}

string UsernameKikCode::username() const
{
    return username_;
}

uint16_t UsernameKikCode::nonce() const
{
    return nonce_;
}

RemoteKikCode::RemoteKikCode(Colour colour)
: KikCode(KikCode::Type::Remote, colour)
{
}

RemoteKikCode::RemoteKikCode(std::string payload, Colour colour)
: KikCode(KikCode::Type::Remote, colour)
, payload_(std::move(payload))
{
}

void RemoteKikCode::decode(uint8_t *data_section)
{
    KikCode::decode(data_section);

    payload_ = string(reinterpret_cast<char *>(data_section + 2), KIK_CODE_PAYLOAD_BYTE_COUNT);
}

void RemoteKikCode::encode(uint8_t *out_data)
{
    memcpy(out_data + 2, payload_.c_str(), KIK_CODE_PAYLOAD_BYTE_COUNT);

    // finish the encoding and write the error correction
    KikCode::encode(out_data);
}

std::string RemoteKikCode::payload() const
{
    return payload_;
}

GroupKikCode::GroupKikCode(Colour colour)
: KikCode(KikCode::Type::Group, colour)
{
}

GroupKikCode::GroupKikCode(const std::string &invite_code, Colour colour)
: KikCode(KikCode::Type::Group, colour)
, invite_code_(invite_code)
{
}

void GroupKikCode::decode(uint8_t *data_section)
{
    KikCode::decode(data_section);

    invite_code_ = string(reinterpret_cast<char *>(data_section + 2), KIK_CODE_PAYLOAD_BYTE_COUNT);
}

void GroupKikCode::encode(uint8_t *out_data)
{
    memcpy(out_data + 2, invite_code_.c_str(), KIK_CODE_PAYLOAD_BYTE_COUNT);

    // finish the encoding and write the error correction
    KikCode::encode(out_data);
}

std::string GroupKikCode::inviteCode() const
{
    return invite_code_;
}

// Add this new JNI function to your kikcode_encoding.cpp or kikcodes.cpp file

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_kik_scan_RemoteKikCode_payload(
        JNIEnv *env,
        jobject thiz) { // 'thiz' refers to the RemoteKikCode instance in Kotlin

    // 1. Find the KikCode base class to get the 'nativePtr' field.
    jclass kikcode_class = env->FindClass("com/kik/scan/KikCode");
    if (kikcode_class == nullptr) {
        return nullptr; // Should not happen if the class exists
    }
    jfieldID native_ptr_field = env->GetFieldID(kikcode_class, "nativePtr", "J");
    if (native_ptr_field == nullptr) {
        return nullptr; // Should not happen if the field exists
    }

    // 2. Get the value of the 'nativePtr' field from the object.
    jlong native_ptr = env->GetLongField(thiz, native_ptr_field);
    if (native_ptr == 0) {
        // The native object has already been destroyed or was never created.
        return nullptr;
    }

    // 3. Cast the pointer back to the correct C++ type.
    // Use static_cast for safety.
    auto* remote_code = static_cast<RemoteKikCode*>(reinterpret_cast<KikCode*>(native_ptr));

    // 4. Call the C++ payload() method to get the data.
    std::string payload_str = remote_code->payload();

    // 5. Create a new Java byte array (jbyteArray).
    jbyteArray result_byte_array = env->NewByteArray(payload_str.length());
    if (result_byte_array == nullptr) {
        return nullptr; // Out of memory
    }

    // 6. Copy the C++ string data into the Java byte array.
    env->SetByteArrayRegion(
            result_byte_array,
            0,
            payload_str.length(),
            reinterpret_cast<const jbyte*>(payload_str.c_str())
    );

    // 7. Return the Java byte array.
    return result_byte_array;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_kik_scan_RemoteKikCode_colour(
        JNIEnv *env,
        jobject thiz) { // 'thiz' refers to the KikCode object instance in Kotlin

    // 1. Find the KikCode class to get the 'nativePtr' field.
    jclass kikcode_class = env->GetObjectClass(thiz);
    if (kikcode_class == nullptr) {
        return 0; // Should not happen
    }
    jfieldID native_ptr_field = env->GetFieldID(kikcode_class, "nativePtr", "J");
    if (native_ptr_field == nullptr) {
        return 0; // Should not happen
    }

    // 2. Get the value of the 'nativePtr' field from the object.
    jlong native_ptr = env->GetLongField(thiz, native_ptr_field);
    if (native_ptr == 0) {
        // The native object has already been destroyed or was never created.
        return 0;
    }

    // 3. Cast the pointer back to the base C++ type.
    auto* kik_code = reinterpret_cast<KikCode*>(native_ptr);

    // 4. Call the C++ colour() method to get the value.
    KikCode::Colour colour_enum = kik_code->colour();

    // 5. Return the value as a jint.
    // The enum value is already an integer, so a direct cast is fine.
    return static_cast<jint>(colour_enum);
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_kik_scan_RemoteKikCode_encode(
        JNIEnv *env,
        jobject thiz) { // 'thiz' refers to the KikCode object instance

    // 1. Find the KikCode class and get the 'nativePtr' field ID.
    jclass kikcode_class = env->GetObjectClass(thiz);
    if (kikcode_class == nullptr) {
        return nullptr;
    }
    jfieldID native_ptr_field = env->GetFieldID(kikcode_class, "nativePtr", "J");
    if (native_ptr_field == nullptr) {
        return nullptr;
    }

    // 2. Get the native C++ pointer from the Kotlin object.
    jlong native_ptr = env->GetLongField(thiz, native_ptr_field);
    if (native_ptr == 0) {
        // Handle cases where the native object is not valid.
        return nullptr;
    }

    // 3. Cast the pointer back to the base C++ KikCode type.
    auto* kik_code = reinterpret_cast<KikCode*>(native_ptr);

    // 4. Prepare an output buffer to hold the encoded data.
    // The total size of an encoded kik code is KIK_CODE_ALL_BYTE_COUNT.
    unsigned char output_buffer[KIK_CODE_ALL_BYTE_COUNT] = {0};

    // 5. Call the native C++ encode() method.
    // This method will fill the output_buffer with the encoded data.
    // We wrap this in a try-catch block in case encode() throws an exception (e.g., invalid_argument).
    try {
        kik_code->encode(output_buffer);
    } catch (const std::exception& e) {
        // Log the error and return null if encoding fails.
        LOGD("Exception during KikCode::encode: %s", e.what());
        return nullptr;
    }

    // 6. Create a new Java byte array (jbyteArray) for the result.
    jbyteArray result_byte_array = env->NewByteArray(KIK_CODE_ALL_BYTE_COUNT);
    if (result_byte_array == nullptr) {
        return nullptr; // Out of memory
    }

    // 7. Copy the encoded data from the C++ buffer into the new Java byte array.
    env->SetByteArrayRegion(
            result_byte_array,
            0,
            KIK_CODE_ALL_BYTE_COUNT,
            reinterpret_cast<const jbyte*>(output_buffer)
    );

    // 8. Return the Java byte array.
    return result_byte_array;
}

// Add this new JNI function to kikcode_encoding.cpp

extern "C"
JNIEXPORT void JNICALL
Java_com_kik_scan_RemoteKikCode_destroyNative(
        JNIEnv *env,
        jobject thiz,
        jlong native_ptr) {

    // 1. Check if the pointer is non-zero to prevent crashing on a double-delete.
    if (native_ptr == 0) {
        LOGD("destroyNative called with a null pointer, nothing to do.");
        return;
    }

    // 2. Cast the long back to a KikCode pointer.
    // We can use the base class pointer because the destructor should be virtual.
    auto* kik_code_to_delete = reinterpret_cast<KikCode*>(native_ptr);

    // 3. Log the action for debugging purposes.
    LOGD("destroyNative: Deleting native KikCode object at address %p", kik_code_to_delete);

    // 4. Delete the C++ object to free its memory.
    delete kik_code_to_delete;
}

// --- Helper to extract native pointer from KikCode subclass instances ---
static KikCode* getNativePtr(JNIEnv *env, jobject thiz) {
    jclass kikcode_class = env->FindClass("com/kik/scan/KikCode");
    if (kikcode_class == nullptr) return nullptr;
    jfieldID native_ptr_field = env->GetFieldID(kikcode_class, "nativePtr", "J");
    if (native_ptr_field == nullptr) return nullptr;
    jlong native_ptr = env->GetLongField(thiz, native_ptr_field);
    if (native_ptr == 0) return nullptr;
    return reinterpret_cast<KikCode*>(native_ptr);
}

// --- UsernameKikCode JNI ---

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kik_scan_UsernameKikCode_username(
        JNIEnv *env,
        jobject thiz) {
    auto* kik_code = getNativePtr(env, thiz);
    if (kik_code == nullptr) return nullptr;
    auto* username_code = static_cast<UsernameKikCode*>(kik_code);
    std::string username = username_code->username();
    return env->NewStringUTF(username.c_str());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_kik_scan_UsernameKikCode_nonce(
        JNIEnv *env,
        jobject thiz) {
    auto* kik_code = getNativePtr(env, thiz);
    if (kik_code == nullptr) return 0;
    auto* username_code = static_cast<UsernameKikCode*>(kik_code);
    return static_cast<jint>(username_code->nonce());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_kik_scan_UsernameKikCode_type(
        JNIEnv *env,
        jobject thiz) {
    auto* kik_code = getNativePtr(env, thiz);
    if (kik_code == nullptr) return 0;
    return static_cast<jint>(kik_code->type());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_kik_scan_UsernameKikCode_colour(
        JNIEnv *env,
        jobject thiz) {
    auto* kik_code = getNativePtr(env, thiz);
    if (kik_code == nullptr) return 0;
    return static_cast<jint>(kik_code->colour());
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_kik_scan_UsernameKikCode_encode(
        JNIEnv *env,
        jobject thiz) {
    auto* kik_code = getNativePtr(env, thiz);
    if (kik_code == nullptr) return nullptr;

    unsigned char output_buffer[KIK_CODE_ALL_BYTE_COUNT] = {0};
    try {
        kik_code->encode(output_buffer);
    } catch (const std::exception& e) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(KIK_CODE_ALL_BYTE_COUNT);
    if (result == nullptr) return nullptr;
    env->SetByteArrayRegion(result, 0, KIK_CODE_ALL_BYTE_COUNT, reinterpret_cast<const jbyte*>(output_buffer));
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_kik_scan_UsernameKikCode_destroyNative(
        JNIEnv *env,
        jobject thiz,
        jlong native_ptr) {
    if (native_ptr == 0) return;
    delete reinterpret_cast<KikCode*>(native_ptr);
}

// --- GroupKikCode JNI ---

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_kik_scan_GroupKikCode_inviteCode(
        JNIEnv *env,
        jobject thiz) {
    auto* kik_code = getNativePtr(env, thiz);
    if (kik_code == nullptr) return nullptr;
    auto* group_code = static_cast<GroupKikCode*>(kik_code);
    std::string invite_code = group_code->inviteCode();

    jbyteArray result = env->NewByteArray(invite_code.length());
    if (result == nullptr) return nullptr;
    env->SetByteArrayRegion(result, 0, invite_code.length(), reinterpret_cast<const jbyte*>(invite_code.c_str()));
    return result;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_kik_scan_GroupKikCode_type(
        JNIEnv *env,
        jobject thiz) {
    auto* kik_code = getNativePtr(env, thiz);
    if (kik_code == nullptr) return 0;
    return static_cast<jint>(kik_code->type());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_kik_scan_GroupKikCode_colour(
        JNIEnv *env,
        jobject thiz) {
    auto* kik_code = getNativePtr(env, thiz);
    if (kik_code == nullptr) return 0;
    return static_cast<jint>(kik_code->colour());
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_kik_scan_GroupKikCode_encode(
        JNIEnv *env,
        jobject thiz) {
    auto* kik_code = getNativePtr(env, thiz);
    if (kik_code == nullptr) return nullptr;

    unsigned char output_buffer[KIK_CODE_ALL_BYTE_COUNT] = {0};
    try {
        kik_code->encode(output_buffer);
    } catch (const std::exception& e) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(KIK_CODE_ALL_BYTE_COUNT);
    if (result == nullptr) return nullptr;
    env->SetByteArrayRegion(result, 0, KIK_CODE_ALL_BYTE_COUNT, reinterpret_cast<const jbyte*>(output_buffer));
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_kik_scan_GroupKikCode_destroyNative(
        JNIEnv *env,
        jobject thiz,
        jlong native_ptr) {
    if (native_ptr == 0) return;
    delete reinterpret_cast<KikCode*>(native_ptr);
}

// --- RemoteKikCode (missing type) ---

extern "C"
JNIEXPORT jint JNICALL
Java_com_kik_scan_RemoteKikCode_type(
        JNIEnv *env,
        jobject thiz) {
    auto* kik_code = getNativePtr(env, thiz);
    if (kik_code == nullptr) return 0;
    return static_cast<jint>(kik_code->type());
}

