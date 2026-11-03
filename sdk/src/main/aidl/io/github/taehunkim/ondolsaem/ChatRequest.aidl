package io.github.taehunkim.ondolsaem;

parcelable ChatRequest {
    /** Chosen by the client; used by IChatService.cancel. */
    String requestId;
    /** Same key + same history size lets the host reuse the model's conversation state. */
    String conversationKey;
    String system;
    /** Earlier turns, oldest first: role 0 = user, 1 = assistant. */
    int[] historyRoles;
    String[] historyTexts;
    String userText;
    /** JPEG/PNG images of the user turn (ignored when the model has no vision). */
    ParcelFileDescriptor[] userImages;
    /** JSON array of {"name","description","parameters"(JSON schema object)}. */
    String toolsJson;
}
