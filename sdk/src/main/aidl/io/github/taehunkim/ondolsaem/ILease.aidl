package io.github.taehunkim.ondolsaem;

/** Keeps a category's model loaded until released (or until the client process dies). */
interface ILease {
    void release() = 1;
}
