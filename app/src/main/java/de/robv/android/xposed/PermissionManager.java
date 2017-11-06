package de.robv.android.xposed;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.JsonReader;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/*package*/ final class PermissionManager {

    private static final String TAG = XposedBridge.TAG;

    private static PackageManager pm;
    private static Map<String, String> modulePathToName = new HashMap<>();
    private static ConcurrentMap<String, Set<String>> permissionMap = new ConcurrentHashMap<>();

    static boolean checkPermission(String modulePath, String packageName) {
        if (modulePath == null) {
            // Method hooked by XposedInit, Not a module implemented hook
            return true;
        }

        if (modulePath != null) {
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
        if (permissionMap.containsKey(moduleName)) {
            Set<String> perms = permissionMap.get(moduleName);
            if (perms.contains(packageName)) {
                granted = true;
            }
        }
        Log.i(TAG, "Permissions for " + moduleName + " to " + packageName + ": " + granted);
        sendNotification(moduleName, packageName, granted);
        return granted;
    }

    private static void sendNotification(String moduleName, String packageName, boolean granted) {
        Application app = AndroidAppHelper.currentApplication();
        if (app != null) {
            Intent sendIntent = new Intent("de.robv.android.xposed.installer.PERMISSION_ACCESS");
            sendIntent.putExtra("some", moduleName + " " + packageName + " " + granted);
            sendIntent.putExtra("moduleName", moduleName);
            sendIntent.putExtra("packageName", packageName);
            sendIntent.putExtra("granted", granted);
            sendIntent.setType("text/plain");
            app.sendBroadcast(sendIntent);
            Log.i(TAG, "Permission broadcast send");
        }
    }


    // Helper methods to read the permissions.json file

    private static class Module {
        String name;
        Set<String> packages;
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
                m.packages = readPackagesSet(reader);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return m;
    }

    private static Set<String> readPackagesSet(JsonReader reader) throws IOException {
        Set<String> packages = new HashSet<String>();
        reader.beginArray();
        while (reader.hasNext()) {
            packages.add(reader.nextString());
        }
        reader.endArray();
        return packages;
    }
}
