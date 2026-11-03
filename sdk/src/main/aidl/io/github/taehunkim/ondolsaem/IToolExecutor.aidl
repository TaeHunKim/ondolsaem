package io.github.taehunkim.ondolsaem;

/** Runs a tool call inside the client (called by the host while it generates an answer). */
interface IToolExecutor {
    String execute(String name, String argsJson) = 1;
}
