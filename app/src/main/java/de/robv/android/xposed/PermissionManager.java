package de.robv.android.xposed;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.JsonReader;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/*package*/ final class PermissionManager {

    private static final String TAG = XposedBridge.TAG;

    private static PackageManager pm;
    private static Map<String, String> modulePathToName = new HashMap<>();
    private static ConcurrentMap<String, Map<String, Boolean>> permissionMap = new ConcurrentHashMap<>();

    static boolean checkPermission(String modulePath, String packageName) {
        if (modulePath == null) {
            // Method hooked by XposedInit, Not a module implemented hook
            return true;
        }

        String moduleName = modulePathToName.get(modulePath);
        if (moduleName == null) {
            if (pm == null) {
                Application app = AndroidAppHelper.currentApplication();
                if (app != null) {
                    pm = app.getPackageManager();
                }
            }
            if (pm != null) {
                PackageInfo pi = pm.getPackageArchiveInfo(modulePath, 0);
                if (pi != null) {
                    moduleName = pi.packageName;
                }
            }
            if (moduleName == null) {
                String[] parts = modulePath.split("/");
                if (parts.length > 1) {
                    moduleName = parts[parts.length - 2];
                    parts = moduleName.split("-");
                    moduleName = parts[0];
                }
            }
            if (moduleName != null) {
                modulePathToName.put(modulePath, moduleName);
            }
        }

        Log.i(TAG, "Checking permissions for " + moduleName + " to " + packageName);
        boolean granted = false;
        boolean unknown = true;
        if (permissionMap.containsKey(moduleName)) {
            Map<String, Boolean> perms = permissionMap.get(moduleName);
            if (perms.containsKey(packageName)) {
                unknown = false;
                granted = perms.get(packageName);
            }
        }
        String status = unknown ? "unknown" : (granted ? "allowed" : "denied");
        Log.i(TAG, "Permissions for " + moduleName + " to " + packageName + ": " + status);
        sendNotification(moduleName, packageName, status);
        return granted;
    }

    private static void sendNotification(String moduleName, String packageName, String status) {
        Application app = AndroidAppHelper.currentApplication();
        if (app != null) {
            Intent sendIntent = new Intent("de.robv.android.xposed.installer.action.PERMISSION_NOTIFICATION");
            sendIntent.putExtra("moduleName", moduleName);
            sendIntent.putExtra("packageName", packageName);
            sendIntent.putExtra("status", status);
            sendIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            sendIntent.setComponent(new ComponentName("de.robv.android.xposed.installer", "de.robv.android.xposed" +
                    ".installer.receivers.PermissionsReceiver"));
            app.sendBroadcast(sendIntent);

            Log.i(TAG, "Permission broadcast send");
        }
    }


    // Helper methods to read the permissions.json file

    private static class Module {
        String name;
        Map<String, Boolean> packages;
    }

    /*package*/
    static void readPermissionsFromStream(InputStream stream) throws IOException {
        JsonReader reader = new JsonReader(new InputStreamReader(stream));
        try {
            readModulesArray(reader);
        } finally {
            reader.close();
        }
        Log.i(TAG, "Loaded Permissions: " + permissionMap.toString());
    }

    private static void readModulesArray(JsonReader reader) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            Module m = readModule(reader);
            permissionMap.put(m.name, m.packages);
        }
        reader.endArray();
    }

    private static Module readModule(JsonReader reader) throws IOException {
        Module m = new Module();

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("name")) {
                m.name = reader.nextString();
            } else if (name.equals("packages")) {
                m.packages = readPackagesMap(reader);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return m;
    }

    private static Map<String, Boolean> readPackagesMap(JsonReader reader) throws IOException {
        Map<String, Boolean> packages = new HashMap<String, Boolean>();
        reader.beginObject();
        while (reader.hasNext()) {
            String packageName = reader.nextName();
            Boolean isAllowed = reader.nextBoolean();
            packages.put(packageName, isAllowed);
        }
        reader.endObject();
        return packages;
    }
}
