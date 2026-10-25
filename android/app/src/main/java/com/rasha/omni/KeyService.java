package com.rasha.omni;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional, off until the person turns it on in the TV's Accessibility settings ("Use these buttons everywhere"). It looks only at key presses that
 * the Remote buttons screen has assigned (INFO and the mic by default), and only while another app is in front: it never reads screen content,
 * never touches other keys, and OMNI itself handles the same keys when it is in front. Everything it does is start OMNI or an app the person chose.
 */
public class KeyService extends AccessibilityService {
    private KeyMap map;
    private long mapAt;

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }

    @Override public void onInterrupt() { }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        int key = KeyMap.keyOf(e.getKeyCode(), e.getScanCode());
        if (KeyMap.reserved(key)) return false;
        if (map == null || System.currentTimeMillis() - mapAt > 3000) { map = new KeyMap(getSharedPreferences("omni", Context.MODE_PRIVATE).getString("keymap", null)); mapAt = System.currentTimeMillis(); }
        String act = map.get(key);
        if (act == null) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) run(act);
        return true;                                              // the whole press is ours: no stray key-up reaches the app underneath
    }

    private void run(String act) {
        try {
            if (act.startsWith("app:")) {
                Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(act.substring(4));
                if (i == null) i = getPackageManager().getLaunchIntentForPackage(act.substring(4));
                if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_KEY_ACTION, act));
        } catch (RuntimeException ignored) { }
    }

    static boolean enabled(Context c) {
        String s = android.provider.Settings.Secure.getString(c.getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(new android.content.ComponentName(c, KeyService.class).flattenToString());
    }
}
