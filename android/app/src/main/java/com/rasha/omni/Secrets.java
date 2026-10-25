package com.rasha.omni;

/** The TMDB credential, read from the git-ignored tmdb.token at build time by Gradle (BuildConfig). Empty when there is none. */
final class Secrets {
    static final String TMDB = BuildConfig.TMDB;

    private Secrets() { }
}
