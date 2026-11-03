package io.github.taehunkim.ondolsaem;

import io.github.taehunkim.ondolsaem.FaceResult;

/** Category "face" (API 2). */
interface IFaceService {
    /**
     * A JPEG or PNG image; returns the faces large enough to recognize, each with its box and identity vector
     * (empty when there are none).
     */
    FaceResult[] detectFaces(in ParcelFileDescriptor image, int priority) = 1;
}
