package com.komron.rostly.violation;

import lombok.Getter;

@Getter
public enum ViolationType {
    TAB_SWITCH(true, true, 4),
    COPY_PASTE(false, false, 1),
    FULLSCREEN_EXIT(true, true, 4),
    MULTIPLE_MONITORS(false, false, 8),
    DEVTOOLS_OPEN(false, false, 1),
    IDLE(false, true, 2),
    FACE_NOT_VISIBLE(true, true, 6),
    CAMERA_OFF(false, true, 5),
    SCREEN_SHARE_OFF(false, true, 6),
    MULTIPLE_FACES(true, true, 10),
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
