package com.komron.rostly.violation;

import lombok.Getter;

@Getter
public enum ViolationType {
    TAB_SWITCH(true, true, 5),
    COPY_PASTE(false, false, 5),
    FULLSCREEN_EXIT(true, true, 5),
    MULTIPLE_MONITORS(true, false, 7),
    IDLE(false, true, 2),
    FACE_NOT_VISIBLE(true, true, 7),
    MULTIPLE_FACES(true, false, 10),
    PHONE_DETECTED(true, false, 10),
    CAMERA_OFF(false, true, 3),
    MICROPHONE_OFF(false, true, 3);

    private final boolean requiresScreenshot;
    private final boolean requiresDuration;
    private final int penaltyScore;

    ViolationType(boolean requiresScreenshot, boolean requiresDuration, int penaltyScore) {
        this.requiresScreenshot = requiresScreenshot;
        this.requiresDuration = requiresDuration;
        this.penaltyScore = penaltyScore;
    }

}
