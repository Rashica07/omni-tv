package com.rasha.omni;

import android.content.Context;

import java.util.List;

/** What pages need from the activity. */
interface Host {
    Context context();
    AppRepo repo();
    Ambient ambient();
    Wallpaper wallpaper();
    Images images();
    Tmdb tmdb();
    /** Launches a sanitised intent (e.g. a Watch Next item) with the same ripple and zoom. */
    void launchIntent(android.view.View src, int x, int y, int w, int h, android.content.Intent intent, String pkg);
    /** Zoom-launches from a rectangle inside src (for custom-drawn tiles). */
    void launchAt(android.view.View src, int x, int y, int w, int h, AppRepo.App app, String listId);
    /** Hold-OK options for an app (move, pin, hide, info, uninstall). */
    void appMenu(AppRepo.App app, String listId);
    /** Shows a bottom sheet of focusable options. Actions should call closeSheet to dismiss it. */
    void showSheet(String title, String sub, List<String> labels, List<Runnable> actions);
    /** Dismisses the open sheet, then runs the given action (may be null) once it is gone. */
    void closeSheet(Runnable after);
    /** A short confirmation capsule at the top of the screen (pinned, hidden, ...). */
    void flash(String heading, String detail);
    /** Re-reads the current page's hint line (also shows the Move-mode instruction while moving). */
    void hintChanged();
    /** A move ended: placed=false means it was cancelled. Shows a short confirmation. */
    void moveFinished(String label, boolean placed);
    /** Moves focus to the currently selected tab in the header. */
    void focusNav();
    /** True while a header tab has focus: pages must not pull focus away from it when their data arrives. */
    boolean navHasFocus();
    /** Opens the Control Center drawer (status and shortcuts) over whatever is showing. */
    void openControlCenter();

    /** The first-run welcome and setup, run again from Settings. */
    void openWelcome();

    /** The voice/typed assistant sheet. */
    void openAssistant();

    /** Settings > Remote buttons. */
    void openRemoteButtons();
    /** Rebuilds every page, restoring focus to the given app in the given list when present. */
    void refreshAll(String focusPkg, String focusList);
}
