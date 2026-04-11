package dev.dev7.v2ray;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class V2rayController {
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    public static void init(Context context, int icon, String appName) {
        // Initialization handled by AAR
    }

    public static void startV2ray(Context context, String remark, String config, String bypassApps) {
        try {
            // We use the full string name of the class to avoid the "cannot find symbol" error
            Intent intent = new Intent();
            intent.setClassName(context.getPackageName(), "dev.dev7.v2ray.V2rayService");
            intent.putExtra("COMMAND", "START_V2RAY");
            intent.putExtra("REMARK", remark);
            intent.putExtra("CONFIG", config);
            intent.putExtra("BYPASS_APPS", bypassApps);
            context.startService(intent);
        } catch (Exception e) {
            Log.e("V2rayController", "Start Error: " + e.getMessage());
        }
    }

    public static void stopV2ray(Context context) {
        try {
            Intent intent = new Intent();
            intent.setClassName(context.getPackageName(), "dev.dev7.v2ray.V2rayService");
            intent.putExtra("COMMAND", "STOP_V2RAY");
            context.startService(intent);
        } catch (Exception e) {
            Log.e("V2rayController", "Stop Error: " + e.getMessage());
        }
    }

    public static int getConnectionState() {
        return STATE_DISCONNECTED; 
    }
}
