package com.rasha.omni;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so the system lets OMNI see which media sessions are active (that is how "Casting to this TV" is noticed). It reads no notifications:
 * every callback is the platform default. Enabled by the person in the setup's permission step, or with adb: cmd notification allow_listener.
 */
public class MediaListener extends NotificationListenerService { }
