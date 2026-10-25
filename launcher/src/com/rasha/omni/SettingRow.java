package com.rasha.omni;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import static com.rasha.omni.Theme.dp;

/** One line of the settings list. Focus = solid white fill with black text (docs/design spec D3). */
final class SettingRow extends LinearLayout {
    private final TextView label;
    private final TextView desc;
    private final GradientDrawable bg;
    private final Spring focus;

    SettingRow(Context c, String name, String description) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setClickable(true);
        bg = new GradientDrawable();
        bg.setCornerRadius(dp(Theme.RADIUS));
        bg.setColor(0);
        setBackground(bg);

        label = Theme.text(new TextView(c), 22, Theme.TEXT2, 650);
        label.setText(name);
        label.setPadding(dp(18), 0, 0, 0);
        addView(label, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        desc = Theme.text(new TextView(c), 13, Theme.TEXT3, 450);
        desc.setText(description);
        desc.setPadding(0, 0, dp(18), 0);
        addView(desc, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        focus = new Spring(420f, 0.85f, 0f, 0.002f, v -> {
            float k = Math.max(0f, Math.min(1f, v));
            bg.setColor(Theme.alpha(Theme.WHITE, (int) (255 * k)));
            label.setTextColor(Theme.lerp(Theme.TEXT2, Theme.BLACK, k));
            desc.setTextColor(Theme.lerp(Theme.TEXT3, 0xFF4A4A4A, k));
        });
    }

    @Override protected void onFocusChanged(boolean gained, int dir, android.graphics.Rect prev) {
        super.onFocusChanged(gained, dir, prev);
        focus.to(gained ? 1f : 0f);
    }
}
