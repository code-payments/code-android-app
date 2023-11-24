#include <jni.h>
#include <string>

#include <memory>
#include <utility>
#include <vector>

#include <kikcodes.h>

#define TOTAL_BYTE_COUNT    39
#define MAIN_BYTE_COUNT     35
#define DATA_BYTE_COUNT     22
#define PAYLOAD_BYTE_COUNT  20
#define ECC_BYTE_COUNT      13

#define ZERO_BYTES { 0 }

std::vector<unsigned char> jbyteArrayToCharVec(
        JNIEnv *env,
        jbyteArray arr
) {
    jboolean isCopy;
    jbyte *arrJb = env->GetByteArrayElements(arr, &isCopy);
    int size = env->GetArrayLength(arr);
    std::vector<unsigned char> arrV((int) (size));
    int i = 0;

    while (i < size) {
        arrV[i] = arrJb[i];
        i++;
    }
    return arrV;
}

std::vector<unsigned char> encode(
        std::vector<unsigned char> dataVec
) {
    unsigned char *data = &dataVec[0];
    unsigned char outData[MAIN_BYTE_COUNT] = ZERO_BYTES;

    kikCodeEncodeRemote(outData, (unsigned char *) data, 0);

    std::vector<unsigned char> outDataVec(outData, outData + MAIN_BYTE_COUNT);
    return outDataVec;
}

extern "C"
jbyteArray
Java_com_getcode_codeScanner_CodeScanner_Encode(
        JNIEnv *env,
        jobject /* this*/,
        jbyteArray data
) {
    std::vector<unsigned char> encoded = encode(jbyteArrayToCharVec(env, data));
    unsigned char *encodedC = &encoded[0];

    jbyteArray output = env->NewByteArray(MAIN_BYTE_COUNT);
    env->SetByteArrayRegion(output, 0, MAIN_BYTE_COUNT, (jbyte *) encodedC);

    return output;
}