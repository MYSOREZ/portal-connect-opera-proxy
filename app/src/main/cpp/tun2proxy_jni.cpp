#include <jni.h>
#include <dlfcn.h>
#include <string>
#include <android/log.h>
#include <pthread.h>
#include "tun2proxy.h"

// Logcat тэг
#define LOG_TAG "OperaProxy"

// Определяем типы функций
typedef int (*pfn_tun2proxy_with_fd_run)(
        const char *proxy_url,
        int tun_fd,
        bool close_fd_on_drop,
        bool packet_information,
        unsigned short tun_mtu,
        Tun2proxyDns dns_strategy,
        Tun2proxyVerbosity verbosity);

typedef int (*pfn_tun2proxy_stop)(void);

// Тип для установки коллбека
typedef void (*pfn_tun2proxy_set_log_callback)(
        void (*callback)(Tun2proxyVerbosity, const char*, void*),
        void *ctx);

// Глобальные переменные
static void* t2p_handle = nullptr;
static pfn_tun2proxy_with_fd_run t2p_run = nullptr;
static pfn_tun2proxy_stop t2p_stop = nullptr;
static pfn_tun2proxy_set_log_callback t2p_set_log = nullptr;

// Глобальные переменные для JNI обратного вызова
static JavaVM* g_jvm = nullptr;
static jobject g_service_ref = nullptr;
static jclass g_service_cls = nullptr;
static jmethodID g_onLog_mid = nullptr;

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;

// TLS для JNIEnv
struct ThreadEnv {
    JNIEnv* env;
    bool attached_by_us;
};

static pthread_key_t g_env_key;
static pthread_once_t g_env_once = PTHREAD_ONCE_INIT;

static void thread_env_destructor(void* ptr) {
    auto* te = reinterpret_cast<ThreadEnv*>(ptr);
    if (te) {
        if (te->attached_by_us && g_jvm) {
            g_jvm->DetachCurrentThread();
        }
        delete te;
    }
}

static void make_env_key() {
    pthread_key_create(&g_env_key, thread_env_destructor);
}

static JNIEnv* get_thread_env() {
    if (!g_jvm) return nullptr;

    pthread_once(&g_env_once, make_env_key);

    auto* te = reinterpret_cast<ThreadEnv*>(pthread_getspecific(g_env_key));
    if (te && te->env) return te->env;

    JNIEnv* env = nullptr;
    int st = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);

    if (st == JNI_OK) {
        te = new ThreadEnv{env, false};
        pthread_setspecific(g_env_key, te);
        return env;
    }

    if (st == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != 0) return nullptr;
        te = new ThreadEnv{env, true};
        pthread_setspecific(g_env_key, te);
        return env;
    }

    return nullptr;
}

// Сохраняем JVM при загрузке библиотеки
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

static void clear_service_refs(JNIEnv* env) {
    if (!env) return;

    pthread_mutex_lock(&g_lock);

    if (g_service_ref) {
        env->DeleteGlobalRef(g_service_ref);
        g_service_ref = nullptr;
    }
    if (g_service_cls) {
        env->DeleteGlobalRef(g_service_cls);
        g_service_cls = nullptr;
    }
    g_onLog_mid = nullptr;

    pthread_mutex_unlock(&g_lock);
}

static void* safe_dlsym(void* handle, const char* name) {
    dlerror(); // clear old errors
    void* sym = dlsym(handle, name);
    const char* err = dlerror();
    if (err != nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "dlsym(%s) error: %s", name, err);
        return nullptr;
    }
    return sym;
}

// Хелпер для загрузки библиотеки
static bool load_tun2proxy_lib() {
    if (t2p_run && t2p_stop) return true;

    if (!t2p_handle) {
        t2p_handle = dlopen("libtun2proxy.so", RTLD_NOW);
        if (!t2p_handle) {
            // Библиотека могла быть уже загружена через System.loadLibrary - пробуем RTLD_DEFAULT
            __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                                "dlopen(libtun2proxy.so) failed, trying RTLD_DEFAULT symbols");
        }
    }

    void* sym_handle = t2p_handle ? t2p_handle : RTLD_DEFAULT;

    if (!t2p_run) {
        t2p_run = reinterpret_cast<pfn_tun2proxy_with_fd_run>(
                safe_dlsym(sym_handle, "tun2proxy_with_fd_run"));
    }
    if (!t2p_stop) {
        t2p_stop = reinterpret_cast<pfn_tun2proxy_stop>(
                safe_dlsym(sym_handle, "tun2proxy_stop"));
    }
    if (!t2p_set_log) {
        t2p_set_log = reinterpret_cast<pfn_tun2proxy_set_log_callback>(
                safe_dlsym(sym_handle, "tun2proxy_set_log_callback"));
    }

    if (!t2p_run || !t2p_stop) {
        if (t2p_handle) {
            dlclose(t2p_handle);
            t2p_handle = nullptr;
        }
        t2p_run = nullptr;
        t2p_stop = nullptr;
        return false;
    }

    return true;
}

// Callback функция, которую будет вызывать Rust
static void tun2proxy_log_callback(Tun2proxyVerbosity level, const char* msg, void* /*ctx*/) {
    // Всегда пишем в Logcat
    int android_prio = ANDROID_LOG_DEBUG;
    switch (level) {
        case Tun2proxyVerbosity_Error: android_prio = ANDROID_LOG_ERROR; break;
        case Tun2proxyVerbosity_Warn:  android_prio = ANDROID_LOG_WARN;  break;
        case Tun2proxyVerbosity_Info:  android_prio = ANDROID_LOG_INFO;  break;
        case Tun2proxyVerbosity_Debug:  android_prio = ANDROID_LOG_DEBUG;  break;
        case Tun2proxyVerbosity_Trace:  android_prio = ANDROID_LOG_VERBOSE;  break;
        default: break;
    }
    __android_log_print(android_prio, LOG_TAG, "%s", msg ? msg : "");

    // Отправляем в Java (UI), если есть ссылка на сервис и methodID
    JNIEnv* env = get_thread_env();
    if (!env) return;

    jobject service_local = nullptr;
    jmethodID mid = nullptr;

    pthread_mutex_lock(&g_lock);
    if (g_service_ref && g_onLog_mid) {
        service_local = env->NewLocalRef(g_service_ref);
        mid = g_onLog_mid;
    }
    pthread_mutex_unlock(&g_lock);

    if (!service_local || !mid) return;

    jstring jMsg = env->NewStringUTF(msg ? msg : "");
    env->CallVoidMethod(service_local, mid, jMsg);
    env->DeleteLocalRef(jMsg);
    env->DeleteLocalRef(service_local);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_operaproxy_ProxyVpnService_startTun2proxy(
        JNIEnv *env,
        jclass /*clazz*/,
        jobject service_instance,
        jstring proxy_url,
        jint tun_fd,
        jboolean close_fd_on_drop,
        jchar tun_mtu,
        jint dns_strategy,
        jint verbosity) {

    if (!load_tun2proxy_lib()) return -1;

    // Устанавливаем ссылки на сервис и кэшируем methodID один раз
    pthread_mutex_lock(&g_lock);

    if (g_service_ref) {
        env->DeleteGlobalRef(g_service_ref);
        g_service_ref = nullptr;
    }
    if (g_service_cls) {
        env->DeleteGlobalRef(g_service_cls);
        g_service_cls = nullptr;
    }
    g_onLog_mid = nullptr;

    g_service_ref = env->NewGlobalRef(service_instance);

    jclass cls_local = env->GetObjectClass(service_instance);
    if (cls_local) {
        g_service_cls = (jclass) env->NewGlobalRef(cls_local);
        env->DeleteLocalRef(cls_local);
    }

    if (g_service_cls) {
        g_onLog_mid = env->GetMethodID(g_service_cls, "onTun2ProxyLog", "(Ljava/lang/String;)V");
        if (!g_onLog_mid) {
            __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                                "Method onTun2ProxyLog not found (maybe obfuscated?)");
        }
    }

    pthread_mutex_unlock(&g_lock);

    if (t2p_set_log) {
        t2p_set_log(tun2proxy_log_callback, nullptr);
    } else {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG,
                            "Symbol tun2proxy_set_log_callback not found");
    }

    const char *nativeString = env->GetStringUTFChars(proxy_url, nullptr);

    int r = t2p_run(
            nativeString,
            tun_fd,
            (bool) close_fd_on_drop,
            false,
            (unsigned short) tun_mtu,
            (Tun2proxyDns) dns_strategy,
            (Tun2proxyVerbosity) verbosity
    );

    env->ReleaseStringUTFChars(proxy_url, nativeString);

    // tun2proxy_run завершился - очищаем ссылки
    clear_service_refs(env);

    return r;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_operaproxy_ProxyVpnService_stopTun2proxy(JNIEnv *env, jclass /*clazz*/) {
    if (!load_tun2proxy_lib()) {
        return -1;
    }

    // Чтобы не удерживать сервис, даже если tun2proxy зависнет
    clear_service_refs(env);

    return t2p_stop ? t2p_stop() : -1;
}