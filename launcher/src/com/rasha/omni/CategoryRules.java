package com.rasha.omni;

/**
 * Pure rules that put an installed app into a category (no Android types, unit tested). Only evidence the platform actually provides is
 * used; an app with no evidence stays uncategorised and is still reachable under All apps.
 *  - the category the app declares (ApplicationInfo.category, Android 8+);
 *  - the "game" flag;
 *  - whether the app publishes channels or "continue watching" items to the TV (so it is a streaming or TV app);
 *  - whether it offers to open video or audio files.
 */
final class CategoryRules {
    static final int NONE = -1, GAMES = 0, STREAMING = 1, MUSIC = 2, TOOLS = 3;

    // ApplicationInfo.CATEGORY_* values (copied so this class stays Android-free)
    static final int C_GAME = 0, C_AUDIO = 1, C_VIDEO = 2, C_IMAGE = 3, C_SOCIAL = 4, C_NEWS = 5, C_MAPS = 6, C_PRODUCTIVITY = 7;

    private CategoryRules() { }

    static int classify(int declared, boolean gameFlag, boolean publishesToTv, boolean opensVideo, boolean opensAudio) {
        if (gameFlag || declared == C_GAME) return GAMES;
        if (declared == C_VIDEO) return STREAMING;
        if (declared == C_AUDIO) return MUSIC;
        if (declared == C_PRODUCTIVITY || declared == C_MAPS) return TOOLS;
        if (declared == C_NEWS || declared == C_SOCIAL || declared == C_IMAGE) return STREAMING;   // media-like apps
        if (publishesToTv) return STREAMING;                    // undeclared, but it feeds the TV's own rows
        if (opensVideo) return STREAMING;
        if (opensAudio) return MUSIC;
        return NONE;
    }

    static String name(int cat) {
        switch (cat) {
            case GAMES: return "Games";
            case STREAMING: return "Streaming and TV";
            case MUSIC: return "Music and audio";
            case TOOLS: return "Tools";
            default: return "Other";
        }
    }
}
