package io.github.taehunkim.ondolsaem;

/** Category "ocr". */
interface IOcrService {
    /** A JPEG or PNG image; returns the recognised text. */
    String recognize(in ParcelFileDescriptor image, int priority) = 1;
}
