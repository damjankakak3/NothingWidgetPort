package dev.dipa.widgets;

import android.content.Context;
import android.hardware.SensorManager;
import android.view.Surface;

/** Live readings stay in memory; persist only when sensing pauses or a session closes. */
public final class CompassState {
    private static volatile float live = Float.NaN;
    private CompassState() {}
    public static float read(Context c) {
        return Float.isFinite(live) ? live : Data.prefs(c).getFloat("compass", Float.NaN);
    }
    public static void set(float value) {
        if (Float.isFinite(value)) live = ((value % 360) + 360) % 360;
    }
    public static void save(Context c) {
        if (Float.isFinite(live)) Data.prefs(c).edit().putFloat("compass", live).apply();
    }
    public static float difference(float a, float b) {
        return Math.abs(((a - b + 540) % 360) - 180);
    }
    public static float bearing(float[] vector, int displayRotation, float[] matrix,
                                float[] remapped, float[] orientation) {
        SensorManager.getRotationMatrixFromVector(matrix, vector);
        int x = SensorManager.AXIS_X, y = SensorManager.AXIS_Y;
        if (displayRotation == Surface.ROTATION_90) { x = SensorManager.AXIS_Y; y = SensorManager.AXIS_MINUS_X; }
        else if (displayRotation == Surface.ROTATION_180) { x = SensorManager.AXIS_MINUS_X; y = SensorManager.AXIS_MINUS_Y; }
        else if (displayRotation == Surface.ROTATION_270) { x = SensorManager.AXIS_MINUS_Y; y = SensorManager.AXIS_X; }
        SensorManager.remapCoordinateSystem(matrix, x, y, remapped);
        SensorManager.getOrientation(remapped, orientation);
        return (float) ((Math.toDegrees(orientation[0]) + 360) % 360);
    }
}
