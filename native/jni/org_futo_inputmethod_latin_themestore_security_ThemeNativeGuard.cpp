#include "org_futo_inputmethod_latin_themestore_security_ThemeNativeGuard.h"

#include <cstdint>
#include <cstring>
#include "defines.h"
#include "jni_common.h"

namespace {

// Trusted Ed25519 public key (32 bytes raw) matching iPlus Theme Hub worker signer
constexpr const char *kTrustedEd25519PubHex =
    "3978e58cf9df854aeb68bb5576d4de2dbc6b159d395a2be898d857295aeff7cd";

static jstring ThemeNativeGuard_getTrustedEd25519PubKeyHex(JNIEnv *env, jclass clazz) {
    (void)clazz;
    return env->NewStringUTF(kTrustedEd25519PubHex);
}

static jboolean ThemeNativeGuard_constantTimeEquals(
    JNIEnv *env,
    jclass clazz,
    jbyteArray a,
    jbyteArray b
) {
    (void)clazz;
    if (a == nullptr || b == nullptr) return JNI_FALSE;
    const jsize lenA = env->GetArrayLength(a);
    const jsize lenB = env->GetArrayLength(b);
    if (lenA != lenB) return JNI_FALSE;

    jbyte *bytesA = env->GetByteArrayElements(a, nullptr);
    jbyte *bytesB = env->GetByteArrayElements(b, nullptr);
    if (bytesA == nullptr || bytesB == nullptr) {
        if (bytesA) env->ReleaseByteArrayElements(a, bytesA, JNI_ABORT);
        if (bytesB) env->ReleaseByteArrayElements(b, bytesB, JNI_ABORT);
        return JNI_FALSE;
    }

    volatile uint8_t diff = 0;
    for (jsize i = 0; i < lenA; ++i) {
        diff |= static_cast<uint8_t>(bytesA[i] ^ bytesB[i]);
    }

    env->ReleaseByteArrayElements(a, bytesA, JNI_ABORT);
    env->ReleaseByteArrayElements(b, bytesB, JNI_ABORT);
    return (diff == 0) ? JNI_TRUE : JNI_FALSE;
}

static const JNINativeMethod sMethods[] = {
    {
        const_cast<char *>("nativeGetTrustedEd25519PubKeyHex"),
        const_cast<char *>("()Ljava/lang/String;"),
        reinterpret_cast<void *>(ThemeNativeGuard_getTrustedEd25519PubKeyHex)
    },
    {
        const_cast<char *>("nativeConstantTimeEquals"),
        const_cast<char *>("([B[B)Z"),
        reinterpret_cast<void *>(ThemeNativeGuard_constantTimeEquals)
    }
};

} // namespace

namespace latinime {
int register_ThemeNativeGuard(JNIEnv *env) {
    const char *const kClassPathName =
        "org/futo/inputmethod/latin/themestore/security/ThemeNativeGuard";
    jclass clazz = env->FindClass(kClassPathName);
    if (!clazz) {
        // Clear pending ClassNotFoundException so standalone subprojects without the IME UI package
        // can still load libjni_latinime cleanly.
        if (env->ExceptionCheck()) env->ExceptionClear();
        return JNI_TRUE;
    }
    const jint rc = env->RegisterNatives(clazz, sMethods, NELEMS(sMethods));
    env->DeleteLocalRef(clazz);
    return (rc == 0) ? JNI_TRUE : JNI_FALSE;
}
} // namespace latinime
