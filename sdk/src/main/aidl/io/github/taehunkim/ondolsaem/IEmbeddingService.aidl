package io.github.taehunkim.ondolsaem;

import io.github.taehunkim.ondolsaem.EmbeddingResult;

/** Category "embedding". Priority: OndolSaem.PRIORITY_INTERACTIVE or PRIORITY_BACKGROUND. */
interface IEmbeddingService {
    EmbeddingResult embedQuery(String text, int priority) = 1;
    EmbeddingResult embedDocument(String text, @nullable String title, int priority) = 2;
    /** A downscaled JPEG. */
    EmbeddingResult embedImage(in ParcelFileDescriptor jpeg, int priority) = 3;
    /** Frames of one clip -> one vector. */
    EmbeddingResult embedImages(in ParcelFileDescriptor[] jpegs, int priority) = 4;
    /** A complete WAV file (16 kHz mono 16-bit). Needs ModelInfo.audio. */
    EmbeddingResult embedAudio(in ParcelFileDescriptor wav, int priority) = 5;
}
