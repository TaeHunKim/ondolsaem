package io.github.taehunkim.ondolsaem;

/**
 * Entry point of the OndolSaem model host (bound service, action OndolSaem.ACTION_BIND).
 * Contract rule: methods are only ever appended, never reordered or removed; check apiVersion() before
 * calling anything newer than API 1.
 */
interface IOndolSaem {
    int apiVersion() = 1;
}
