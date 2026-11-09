// JNI bridge between com.lucknow.llm.LlamaNative and llama.cpp.
//
// One Session owns one loaded GGUF model plus its context. Generation is
// serialised with a mutex because a llama_context is not thread-safe; the app
// already runs inference off the main thread.

#include <jni.h>
#include <android/log.h>

#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

namespace {

constexpr const char * kTag = "LuckNowLLM";

struct Session {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    std::mutex mutex;
};

void log_error(const char * message) {
    __android_log_print(ANDROID_LOG_ERROR, kTag, "%s", message);
}

void log_info(const char * message) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "%s", message);
}

std::string to_string(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::once_flag g_backend_once;

void ensure_backend() {
    std::call_once(g_backend_once, []() {
        llama_backend_init();
        log_info("llama.cpp backend initialised");
    });
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_lucknow_llm_LlamaNative_loadModel(
        JNIEnv * env, jobject /* this */, jstring modelPath, jint contextSize, jint threads) {
    ensure_backend();

    const std::string path = to_string(env, modelPath);
    if (path.empty()) {
        log_error("empty model path");
        return 0;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;  // CPU only: no assumptions about the device GPU.

    llama_model * model = llama_model_load_from_file(path.c_str(), model_params);
    if (model == nullptr) {
        log_error("failed to load GGUF model");
        return 0;
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(contextSize > 0 ? contextSize : 4096);
    context_params.n_batch = 2048;
    context_params.n_ubatch = 512;
    context_params.n_threads = threads > 0 ? threads : 4;
    context_params.n_threads_batch = context_params.n_threads;
    context_params.no_perf = true;

    llama_context * context = llama_init_from_model(model, context_params);
    if (context == nullptr) {
        log_error("failed to create llama context");
        llama_model_free(model);
        return 0;
    }

    auto * session = new Session();
    session->model = model;
    session->ctx = context;
    session->vocab = llama_model_get_vocab(model);
    log_info("model loaded");
    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lucknow_llm_LlamaNative_generate(
        JNIEnv * env,
        jobject /* this */,
        jlong handle,
        jstring prompt,
        jint maxTokens,
        jfloat temperature) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr || session->ctx == nullptr || session->vocab == nullptr) {
        log_error("generate called without a loaded model");
        return env->NewStringUTF("");
    }

    const std::string text = to_string(env, prompt);
    if (text.empty()) {
        log_error("empty prompt");
        return env->NewStringUTF("");
    }

    std::lock_guard<std::mutex> lock(session->mutex);

    const int32_t needed = -llama_tokenize(
            session->vocab, text.c_str(), static_cast<int32_t>(text.size()), nullptr, 0, true, true);
    if (needed <= 0) {
        log_error("tokenisation failed");
        return env->NewStringUTF("");
    }

    const int32_t context_limit = static_cast<int32_t>(llama_n_ctx(session->ctx));
    if (needed >= context_limit - 8) {
        log_error("prompt does not fit in the model context");
        return env->NewStringUTF("");
    }

    std::vector<llama_token> tokens(static_cast<size_t>(needed));
    if (llama_tokenize(
                session->vocab,
                text.c_str(),
                static_cast<int32_t>(text.size()),
                tokens.data(),
                needed,
                true,
                true) < 0) {
        log_error("tokenisation failed (second pass)");
        return env->NewStringUTF("");
    }

    // Each request is stateless: clear the KV cache before decoding.
    llama_memory_clear(llama_get_memory(session->ctx), true);

    llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()));
    if (llama_decode(session->ctx, batch) != 0) {
        log_error("prompt decode failed");
        return env->NewStringUTF("");
    }

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    if (temperature <= 0.01f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    std::string output;
    const int limit = maxTokens > 0 ? maxTokens : 256;
    for (int index = 0; index < limit; ++index) {
        const llama_token token = llama_sampler_sample(sampler, session->ctx, -1);
        llama_sampler_accept(sampler, token);

        if (llama_vocab_is_eog(session->vocab, token)) break;

        char piece[256];
        const int32_t length =
                llama_token_to_piece(session->vocab, token, piece, sizeof(piece), 0, true);
        if (length > 0) {
            output.append(piece, static_cast<size_t>(length));
            const size_t stop = output.find("<|im_end|>");
            if (stop != std::string::npos) {
                output.resize(stop);
                break;
            }
        }

        llama_token next_token = token;
        llama_batch next = llama_batch_get_one(&next_token, 1);
        if (llama_decode(session->ctx, next) != 0) {
            log_error("continuation decode failed");
            break;
        }
    }

    llama_sampler_free(sampler);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_lucknow_llm_LlamaNative_release(JNIEnv * /* env */, jobject /* this */, jlong handle) {
    auto * session = reinterpret_cast<Session *>(handle);
    if (session == nullptr) return;

    {
        std::lock_guard<std::mutex> lock(session->mutex);
        if (session->ctx != nullptr) llama_free(session->ctx);
        if (session->model != nullptr) llama_model_free(session->model);
        session->ctx = nullptr;
        session->model = nullptr;
        session->vocab = nullptr;
    }
    delete session;
    log_info("model released");
}
