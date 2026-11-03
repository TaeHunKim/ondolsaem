package io.github.taehunkim.ondolsaem;

interface ICueCallback {
    /** One recognised utterance. Return false to stop. */
    boolean onCue(long startMs, long endMs, String text) = 1;
}
