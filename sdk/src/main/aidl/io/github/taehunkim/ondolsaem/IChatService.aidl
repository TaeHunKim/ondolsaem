package io.github.taehunkim.ondolsaem;

import io.github.taehunkim.ondolsaem.ChatRequest;
import io.github.taehunkim.ondolsaem.IChatCallback;
import io.github.taehunkim.ondolsaem.IToolExecutor;

/** Category "llm". */
interface IChatService {
    /** Starts an answer and returns at once; results arrive on the callback. */
    void reply(in ChatRequest request, IChatCallback callback, @nullable IToolExecutor tools, int priority) = 1;
    void cancel(String requestId) = 2;
    /** Drops cached conversation state (the client's history changed). */
    void reset() = 3;
}
