package dev.dipa.widgets;

import android.service.notification.*;
import android.media.*;
import android.media.session.*;
import android.content.*;
import android.os.*;
import java.util.*;

public final class MediaService extends NotificationListenerService {
    private MediaSessionManager manager;
    private MediaController current;
    private BluetoothBatteries batteryReceiver;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { Widgets.updateGroup(MediaService.this, "Media"); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { Widgets.updateGroup(MediaService.this, "Media"); }
        @Override public void onSessionDestroyed() { select(); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener listener = sessions -> select();
    @Override public void onListenerConnected() {
        batteryReceiver=BluetoothBatteries.listen(this);
        manager = getSystemService(MediaSessionManager.class);
        manager.addOnActiveSessionsChangedListener(listener, new ComponentName(this, MediaService.class));
        select();
    }
    private void select() {
        if (current != null) current.unregisterCallback(callback);
        current = controller(this);
        if (current != null) current.registerCallback(callback);
        Widgets.updateGroup(this, "Media");
    }
    @Override public void onListenerDisconnected() {
        if(batteryReceiver!=null){unregisterReceiver(batteryReceiver);batteryReceiver=null;}
        if (manager != null) manager.removeOnActiveSessionsChangedListener(listener);
        if (current != null) current.unregisterCallback(callback);
        manager = null; current = null;
    }
    public static MediaController controller(Context c) {
        try {
            List<MediaController> sessions = c.getSystemService(MediaSessionManager.class).getActiveSessions(new ComponentName(c, MediaService.class));
            for (MediaController session : sessions) {
                PlaybackState s = session.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) return session;
            }
            return sessions.isEmpty() ? null : sessions.get(0);
        } catch (SecurityException e) { return null; }
    }
    public static void control(Context c, String action) {
        MediaController session = controller(c);
        if (session == null) return;
        MediaController.TransportControls t = session.getTransportControls();
        if (action.equals("next")) t.skipToNext();
        else if (action.equals("previous")) t.skipToPrevious();
        else {
            PlaybackState state = session.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) t.pause(); else t.play();
        }
    }
}
