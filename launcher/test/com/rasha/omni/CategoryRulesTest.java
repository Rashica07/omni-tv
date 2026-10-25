package com.rasha.omni;

final class CategoryRulesTest {
    static void run() {
        T.ok(CategoryRules.classify(CategoryRules.C_GAME, false, false, false, false) == CategoryRules.GAMES, "declared game");
        T.ok(CategoryRules.classify(-1, true, false, false, false) == CategoryRules.GAMES, "game flag wins without a declared category");
        T.ok(CategoryRules.classify(CategoryRules.C_VIDEO, false, false, false, false) == CategoryRules.STREAMING, "declared video is streaming");
        T.ok(CategoryRules.classify(CategoryRules.C_AUDIO, false, false, false, false) == CategoryRules.MUSIC, "declared audio is music");
        // real devices: Netflix and YouTube declare nothing but publish rows to the TV
        T.ok(CategoryRules.classify(-1, false, true, false, false) == CategoryRules.STREAMING, "undeclared app that publishes to the TV is streaming");
        T.ok(CategoryRules.classify(-1, false, false, true, false) == CategoryRules.STREAMING, "an app that opens video files is streaming/TV");
        T.ok(CategoryRules.classify(-1, false, false, false, true) == CategoryRules.MUSIC, "an app that opens audio files is music");
        // no evidence = no category: never guessed
        T.ok(CategoryRules.classify(-1, false, false, false, false) == CategoryRules.NONE, "no evidence stays uncategorised");
        // a declared category outranks weaker signals
        T.ok(CategoryRules.classify(CategoryRules.C_PRODUCTIVITY, false, true, true, true) == CategoryRules.TOOLS, "declared productivity stays a tool");
        T.ok(CategoryRules.classify(CategoryRules.C_GAME, false, true, true, true) == CategoryRules.GAMES, "declared game outranks publishing");
        T.ok("Games".equals(CategoryRules.name(CategoryRules.GAMES)) && "Other".equals(CategoryRules.name(CategoryRules.NONE)), "names");
    }
}
