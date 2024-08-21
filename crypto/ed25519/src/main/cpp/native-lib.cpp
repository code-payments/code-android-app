#include <jni.h>
#include <string>

#include <memory>
#include <utility>
#include <vector>

#include <ed25519.h>
#include <base64.hpp>


class KeyPair {
public:
    std::vector<unsigned char> publicKey;
    std::vector<unsigned char> privateKey;
    KeyPair(
            std::vector<unsigned char>&& pub,
            std::vector<unsigned char>&& pri
    ):
            publicKey(std::move(pub)),
            privateKey(std::move(pri))
    {}
};

template<typename T>
std::unique_ptr<T> vector2UnsignedCharPointer(
        std::vector<T> vec
){
    std::unique_ptr<T> res(new T[sizeof(T)*vec.size()+1]);
    size_t pos = 0;
    for(auto c : vec){
        res.get()[pos] = c;
        pos++;
    }
    res.get()[pos] = '\0';
    return res;
}

template<typename T>
std::vector<T> pointer2Vector(
        std::unique_ptr<T>&& array,
        size_t length
) {
    std::vector<T> res(length);
    res.assign(array.get(),array.get()+length);
    return res;
}

jbyteArray charVectorToJbyteArray(
        JNIEnv *env,
        std::vector<unsigned char> vec
) {
    jbyteArray arr = env->NewByteArray(64);
    unsigned char *buf = &vec[0];
    env->SetByteArrayRegion(arr, 0, 64, reinterpret_cast<const jbyte *>(buf));

    return arr;
}

bool verify(
        std::vector<unsigned char> sig,
        std::vector<unsigned char> message,
        std::vector<unsigned char> pubKey) {
    return ed25519_verify(
            vector2UnsignedCharPointer(sig).get(),
            vector2UnsignedCharPointer(message).get(),
            message.size(),
            vector2UnsignedCharPointer(pubKey).get()) == 1;
}

bool onCurve(std::vector<unsigned char> publicKey) {
    return ed25519_on_curve(
            vector2UnsignedCharPointer(publicKey).get()) == 1;
}

std::vector<unsigned char> sign(
        std::vector<unsigned char> message,
        std::vector<unsigned char> publicKey,
        std::vector<unsigned char> privateKey
){
    std::unique_ptr<unsigned char> signatureRaw(new unsigned char[sizeof(unsigned char)*64]);

    ed25519_sign(
            signatureRaw.get(),
            vector2UnsignedCharPointer<unsigned char>(message).get(),
            message.size(),
            vector2UnsignedCharPointer<unsigned char>(std::move(publicKey)).get(),
            vector2UnsignedCharPointer<unsigned char>(std::move(privateKey)).get()
    );

    return pointer2Vector(std::move(signatureRaw), 64);
}

KeyPair generateKeyPair(std::string seed) {
    std::unique_ptr<unsigned char> publicKeyRaw(new unsigned char[sizeof(unsigned char)*32]);
    std::unique_ptr<unsigned char> privateKeyRaw(new unsigned char[sizeof(unsigned char)*64]);

    std::unique_ptr<unsigned char> seedP = vector2UnsignedCharPointer<unsigned char>(
            base64::decode(seed)
    );

    ed25519_create_keypair(
            publicKeyRaw.get(),
            privateKeyRaw.get(),
            seedP.get()
    );

    return KeyPair(
            pointer2Vector(std::move(publicKeyRaw), 32),
            pointer2Vector(std::move(privateKeyRaw), 64)
    );
}

extern "C"
jobject
Java_com_getcode_ed25519_Ed25519_GenerateKeyPair(
        JNIEnv* env,
        jobject /* this*/,
        jstring seed) {

    std::string seedS = std::string( env->GetStringUTFChars( seed, 0));
    KeyPair pair = generateKeyPair(seedS);

    jclass clazz = (*env).FindClass("java/util/ArrayList");
    jobject obj = (*env).NewObject(clazz, (*env).GetMethodID(clazz, "<init>", "()V"));

    std::string privateKeyb64 = base64::encode(pair.privateKey);
    std::string publicKeyb64 = base64::encode(pair.publicKey);

    (*env).CallBooleanMethod(obj, (*env).GetMethodID(clazz, "add", "(Ljava/lang/Object;)Z"),
                             (*env).NewStringUTF(privateKeyb64.c_str()));
    (*env).CallBooleanMethod(obj, (*env).GetMethodID(clazz, "add","(Ljava/lang/Object;)Z"),
                             (*env).NewStringUTF(publicKeyb64.c_str()));

    return obj;
}

std::vector<unsigned char> createSeed16() {
    std::unique_ptr<unsigned char> seed(new unsigned char[sizeof(unsigned char)*16]);
    ed25519_create_seed_16(seed.get());
    return pointer2Vector(std::move(seed), 16);
}

std::vector<unsigned char> createSeed32() {
    std::unique_ptr<unsigned char> seed(new unsigned char[sizeof(unsigned char)*32]);
    ed25519_create_seed_32(seed.get());
    return pointer2Vector(std::move(seed), 32);
}

extern "C"
jbyteArray
Java_com_getcode_ed25519_Ed25519_CreateSeed16(
        JNIEnv* env,
        jobject /* this*/) {
    std::vector<unsigned char> seed = createSeed16();
    unsigned char *seedC = &seed[0];

    jbyteArray output = env->NewByteArray(16);
    env->SetByteArrayRegion(output, 0, 16, (jbyte *) seedC);
    return output;
}

extern "C"
jbyteArray
Java_com_getcode_ed25519_Ed25519_CreateSeed32(
        JNIEnv* env,
        jobject /* this*/) {
    std::vector<unsigned char> seed = createSeed32();
    unsigned char *seedC = &seed[0];

    jbyteArray output = env->NewByteArray(32);
    env->SetByteArrayRegion(output, 0, 32, (jbyte *) seedC);
    return output;
}

std::vector<unsigned char> jbyteArrayToCharVector(
        JNIEnv* env,
        jbyteArray arr
) {
    jboolean isCopy;
    jbyte * arrJb = env->GetByteArrayElements(arr, &isCopy);
    int size = env->GetArrayLength(arr);
    std::vector<unsigned char> arrV((int)(size));
    int i = 0;

    while (i < size) {
        arrV[i] = arrJb[i];
        i++;
    }
    return arrV;
}

extern "C"
jbyteArray
Java_com_getcode_ed25519_Ed25519_Signature(
        JNIEnv* env,
        jobject /* this*/,
        jbyteArray aMessage,
        jbyteArray aPriKey,
        jbyteArray aPubKey
) {
    std::vector<unsigned char> v = sign(
            jbyteArrayToCharVector(env, aMessage),
            jbyteArrayToCharVector(env, aPubKey),
            jbyteArrayToCharVector(env, aPriKey)
    );

    return charVectorToJbyteArray(env, v);
}

extern "C"
jboolean
Java_com_getcode_ed25519_Ed25519_Verify(
        JNIEnv* env,
        jobject /* this*/,
        jbyteArray sig,
        jbyteArray message,
        jbyteArray pubKey
) {
    return (jboolean)verify(
            jbyteArrayToCharVector(env, sig),
            jbyteArrayToCharVector(env, message),
            jbyteArrayToCharVector(env, pubKey)
        );
}

extern "C"
jboolean
Java_com_getcode_ed25519_Ed25519_OnCurve(
        JNIEnv* env,
        jobject /* this*/,
        jbyteArray aPubKey
) {

    return (jboolean)onCurve(jbyteArrayToCharVector(env, aPubKey));
}