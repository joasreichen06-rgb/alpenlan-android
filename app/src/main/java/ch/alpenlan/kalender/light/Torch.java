package ch.alpenlan.kalender.light;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.SystemClock;

/** Steuert die Taschenlampe (Blitz-LED) über die Camera2-API. */
public final class Torch {
    private final CameraManager cm;
    private String cameraId;
    private boolean on;
    private long lastSwitch;

    public Torch(Context ctx) {
        cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
        try {
            String fallback = null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash == null || !flash) continue;
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { cameraId = id; break; }
                if (fallback == null) fallback = id;
            }
            if (cameraId == null) cameraId = fallback;
        } catch (Exception ignored) {
            cameraId = null;
        }
    }

    public boolean available() { return cameraId != null; }

    /** Schaltet die Lampe; zu schnelles Umschalten (unter 50 ms) wird ausgelassen. */
    public void set(boolean wanted) {
        if (cameraId == null || wanted == on) return;
        long t = SystemClock.elapsedRealtime();
        if (t - lastSwitch < 50) return;
        lastSwitch = t;
        on = wanted;
        try { cm.setTorchMode(cameraId, wanted); } catch (Exception ignored) { }
    }

    public void off() {
        if (cameraId == null) return;
        on = false;
        try { cm.setTorchMode(cameraId, false); } catch (Exception ignored) { }
    }
}
