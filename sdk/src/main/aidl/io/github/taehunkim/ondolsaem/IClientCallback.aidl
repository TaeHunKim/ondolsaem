package io.github.taehunkim.ondolsaem;

import io.github.taehunkim.ondolsaem.ModelInfo;

oneway interface IClientCallback {
    /** The category's default model changed (or became ready / unavailable). */
    void onModelChanged(in ModelInfo info) = 1;
}
