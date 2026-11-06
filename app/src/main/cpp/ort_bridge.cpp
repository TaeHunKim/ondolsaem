// JNI bridge: one ONNX model, float32 input (or several int64 inputs of one shape) and float32 output, run on the libonnxruntime.so bundled by sherpa-onnx.
#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "onnxruntime_c_api.h"

#define TAG "OndolSaem.Ort"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {
const OrtApi* api() {
    static const OrtApi* a = [] () -> const OrtApi* {
        void* lib = dlopen("libonnxruntime.so", RTLD_NOW);
        if (!lib) { LOGE("dlopen: %s", dlerror()); return nullptr; }
        using GetApiBase = const OrtApiBase* (*)();
        auto get = reinterpret_cast<GetApiBase>(dlsym(lib, "OrtGetApiBase"));
        if (!get) { LOGE("dlsym OrtGetApiBase: %s", dlerror()); return nullptr; }
        const OrtApi* r = get()->GetApi(ORT_API_VERSION);
        if (!r) LOGE("ONNX Runtime %s does not offer API %d", get()->GetVersionString(), ORT_API_VERSION);
        return r;
    }();
    return a;
}

struct Model {
    OrtEnv* env = nullptr;
    OrtSession* session = nullptr;
    std::string input, output;
};

/** Throws IllegalStateException for a failed ORT call; returns true if it failed. */
bool failed(JNIEnv* env, OrtStatus* s) {
    if (!s) return false;
    std::string msg = std::string("ONNX Runtime: ") + api()->GetErrorMessage(s);
    api()->ReleaseStatus(s);
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), msg.c_str());
    return true;
}

std::string name(JNIEnv* env, OrtSession* s, bool input) {
    OrtAllocator* alloc = nullptr;
    api()->GetAllocatorWithDefaultOptions(&alloc);
    char* n = nullptr;
    OrtStatus* st = input ? api()->SessionGetInputName(s, 0, alloc, &n) : api()->SessionGetOutputName(s, 0, alloc, &n);
    if (failed(env, st)) return {};
    std::string r(n);
    alloc->Free(alloc, n);
    return r;
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_taehunkim_ondolsaem_engine_OrtModel_nativeOpen(JNIEnv* env, jclass, jstring path, jint threads, jint optLevel) {
    if (!api()) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "ONNX Runtime not available"); return 0; }
    auto* m = new Model();
    OrtSessionOptions* opts = nullptr;
    const char* p = env->GetStringUTFChars(path, nullptr);
    bool bad = failed(env, api()->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "ondolsaem", &m->env)) ||
               failed(env, api()->CreateSessionOptions(&opts)) ||
               failed(env, api()->SetIntraOpNumThreads(opts, threads)) ||
               failed(env, api()->SetSessionGraphOptimizationLevel(opts, static_cast<GraphOptimizationLevel>(optLevel))) ||
               failed(env, api()->CreateSession(m->env, p, opts, &m->session));
    env->ReleaseStringUTFChars(path, p);
    if (opts) api()->ReleaseSessionOptions(opts);
    if (!bad) { m->input = name(env, m->session, true); m->output = name(env, m->session, false); }
    if (bad || env->ExceptionCheck()) {
        if (m->session) api()->ReleaseSession(m->session);
        if (m->env) api()->ReleaseEnv(m->env);
        delete m;
        return 0;
    }
    return reinterpret_cast<jlong>(m);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_io_github_taehunkim_ondolsaem_engine_OrtModel_nativeRun(JNIEnv* env, jclass, jlong handle, jfloatArray input, jlongArray shape) {
    auto* m = reinterpret_cast<Model*>(handle);
    jsize n = env->GetArrayLength(input);
    std::vector<float> in(n);
    env->GetFloatArrayRegion(input, 0, n, in.data());
    jsize dims = env->GetArrayLength(shape);
    std::vector<int64_t> sh(dims);
    env->GetLongArrayRegion(shape, 0, dims, reinterpret_cast<jlong*>(sh.data()));

    OrtMemoryInfo* mem = nullptr;
    OrtValue* inValue = nullptr;
    OrtValue* outValue = nullptr;
    jfloatArray result = nullptr;
    const char* inName = m->input.c_str();
    const char* outName = m->output.c_str();
    if (!failed(env, api()->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &mem)) &&
        !failed(env, api()->CreateTensorWithDataAsOrtValue(mem, in.data(), in.size() * sizeof(float), sh.data(), sh.size(),
                                                            ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &inValue)) &&
        !failed(env, api()->Run(m->session, nullptr, &inName, &inValue, 1, &outName, 1, &outValue))) {
        OrtTensorTypeAndShapeInfo* info = nullptr;
        size_t count = 0;
        float* data = nullptr;
        if (!failed(env, api()->GetTensorTypeAndShape(outValue, &info)) &&
            !failed(env, api()->GetTensorShapeElementCount(info, &count)) &&
            !failed(env, api()->GetTensorMutableData(outValue, reinterpret_cast<void**>(&data)))) {
            result = env->NewFloatArray(static_cast<jsize>(count));
            env->SetFloatArrayRegion(result, 0, static_cast<jsize>(count), data);
        }
        if (info) api()->ReleaseTensorTypeAndShapeInfo(info);
    }
    if (outValue) api()->ReleaseValue(outValue);
    if (inValue) api()->ReleaseValue(inValue);
    if (mem) api()->ReleaseMemoryInfo(mem);
    return result;
}

/** Several int64 inputs of the same [shape] (e.g. input_ids + attention_mask), concatenated in [data]; first output. */
extern "C" JNIEXPORT jfloatArray JNICALL
Java_io_github_taehunkim_ondolsaem_engine_OrtModel_nativeRunLong(JNIEnv* env, jclass, jlong handle, jobjectArray names,
                                                                  jlongArray data, jlongArray shape) {
    auto* m = reinterpret_cast<Model*>(handle);
    jsize dims = env->GetArrayLength(shape);
    std::vector<int64_t> sh(dims);
    env->GetLongArrayRegion(shape, 0, dims, reinterpret_cast<jlong*>(sh.data()));
    size_t per = 1;
    for (auto d : sh) per *= static_cast<size_t>(d);
    jsize count = env->GetArrayLength(names);
    if (static_cast<size_t>(env->GetArrayLength(data)) != per * count) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "input size does not match shape");
        return nullptr;
    }
    std::vector<int64_t> in(per * count);
    env->GetLongArrayRegion(data, 0, static_cast<jsize>(in.size()), reinterpret_cast<jlong*>(in.data()));
    std::vector<std::string> nameStore(count);
    std::vector<const char*> inNames(count);
    for (jsize i = 0; i < count; i++) {
        auto js = static_cast<jstring>(env->GetObjectArrayElement(names, i));
        const char* c = env->GetStringUTFChars(js, nullptr);
        nameStore[i] = c;
        env->ReleaseStringUTFChars(js, c);
        env->DeleteLocalRef(js);
        inNames[i] = nameStore[i].c_str();
    }

    OrtMemoryInfo* mem = nullptr;
    std::vector<OrtValue*> inValues(count, nullptr);
    OrtValue* outValue = nullptr;
    jfloatArray result = nullptr;
    const char* outName = m->output.c_str();
    bool ok = !failed(env, api()->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &mem));
    for (jsize i = 0; ok && i < count; i++) {
        ok = !failed(env, api()->CreateTensorWithDataAsOrtValue(mem, in.data() + per * i, per * sizeof(int64_t), sh.data(), sh.size(),
                                                                 ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64, &inValues[i]));
    }
    if (ok && !failed(env, api()->Run(m->session, nullptr, inNames.data(), inValues.data(), count, &outName, 1, &outValue))) {
        OrtTensorTypeAndShapeInfo* info = nullptr;
        size_t n = 0;
        float* out = nullptr;
        if (!failed(env, api()->GetTensorTypeAndShape(outValue, &info)) &&
            !failed(env, api()->GetTensorShapeElementCount(info, &n)) &&
            !failed(env, api()->GetTensorMutableData(outValue, reinterpret_cast<void**>(&out)))) {
            result = env->NewFloatArray(static_cast<jsize>(n));
            env->SetFloatArrayRegion(result, 0, static_cast<jsize>(n), out);
        }
        if (info) api()->ReleaseTensorTypeAndShapeInfo(info);
    }
    if (outValue) api()->ReleaseValue(outValue);
    for (auto* v : inValues) if (v) api()->ReleaseValue(v);
    if (mem) api()->ReleaseMemoryInfo(mem);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_taehunkim_ondolsaem_engine_OrtModel_nativeClose(JNIEnv*, jclass, jlong handle) {
    auto* m = reinterpret_cast<Model*>(handle);
    if (!m) return;
    if (m->session) api()->ReleaseSession(m->session);
    if (m->env) api()->ReleaseEnv(m->env);
    delete m;
}
