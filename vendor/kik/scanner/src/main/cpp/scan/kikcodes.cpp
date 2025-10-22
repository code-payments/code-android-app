#include <jni.h>
#include <string>
#include <utility>

#include <android/log.h>
#include "kikcode_scan.h"
#include "kikcodes.h"
#include "../encoding/kikcode_encoding.h"

#include <cstring>
#include <iostream>

using namespace std;

int kikCodeEncodeUsername(
        unsigned char *out_data,
        const char *username,
        const unsigned int username_length,
        const unsigned short nonce,
        const unsigned int colour_code) {
    UsernameKikCode kik_code(string(username, username_length), nonce,
                             (KikCode::Colour) colour_code);

    kik_code.encode(out_data);

    return KIK_CODE_RESULT_SUCCESS;
}

int kikCodeEncodeGroup(
        unsigned char *out_data,
        const unsigned char *invite_code,
        const unsigned int colour_code) {
    GroupKikCode kik_code(string((const char *)invite_code,
    20), (KikCode::Colour) colour_code);

    kik_code.encode(out_data);

    return KIK_CODE_RESULT_SUCCESS;
}

int kikCodeEncodeRemote(
        unsigned char *out_data,
        const unsigned char *key,
        const unsigned int colour_code) {
    RemoteKikCode kik_code(string((const char *)key,
    20), (KikCode::Colour) colour_code);

    kik_code.encode(out_data);

    return KIK_CODE_RESULT_SUCCESS;
}
