package io.github.taehunkim.ondolsaem;

oneway interface IChatCallback {
    void onToken(String text) = 1;
    void onToolRan(String name, String argsJson, String result) = 2;
    void onDone() = 3;
    void onError(int code, String message) = 4;
}
