package org.apache.cordova.xapkreader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

import com.android.vending.expansion.zipfile.APKExpansionSupport;
import com.android.vending.expansion.zipfile.ZipResourceFile;
import com.google.android.vending.expansion.downloader.Helpers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

import org.json.JSONObject;


public class XAPKReader extends CordovaPlugin {

    private static final String LOG_TAG = "XAPKReader";

    private int mainVersion = 1;

    private long mainFileSize = 0L;

    private int patchVersion = 0;

    private long patchFileSize = 0L;

    private boolean downloadOption = true;

    /**
     * Executes the request.
     *
     * This method is called from the WebView thread. To do a non-trivial amount of work, use:
     *     cordova.getThreadPool().execute(runnable);
     *
     * To run on the UI thread, use:
     *     cordova.getActivity().runOnUiThread(runnable);
     *
     * @param action          The action to execute.
     * @param args            The exec() arguments.
     * @param callbackContext The callback context used when calling back into JavaScript.
     * @return                Whether the action was valid.
     * @throws JSONException
     *
     * @sa https://github.com/apache/cordova-android/blob/master/framework/src/org/apache/cordova/CordovaPlugin.java
     */
    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        final Bundle bundle = new Bundle();

        // set defaults
        bundle.putInt("mainVersion", mainVersion);
        bundle.putInt("patchVersion", patchVersion);
        bundle.putLong("mainFileSize", mainFileSize);
        bundle.putLong("patchFileSize", patchFileSize);
        bundle.putBoolean("downloadOption", downloadOption);

        if (action.equals("setExpansionOptions")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        JSONObject expansionInfo = args.getJSONObject(0);
                        mainVersion = expansionInfo.getInt("mainVersion");
                        patchVersion = expansionInfo.getInt("patchVersion");
                        mainFileSize = expansionInfo.getLong("mainFileSize");
                        patchFileSize = expansionInfo.getLong("patchFileSize");
                        downloadOption = expansionInfo.getBoolean("downloadOption");

                        bundle.putInt("mainVersion", mainVersion);
                        bundle.putInt("patchVersion", patchVersion);
                        bundle.putLong("mainFileSize", mainFileSize);
                        bundle.putLong("patchFileSize", patchFileSize);
                        bundle.putBoolean("downloadOption", downloadOption);

                        callbackContext.success("success");
                    }
                    catch (JSONException e) {
                        callbackContext.error(e.getLocalizedMessage());
                    }
                }
            });

            return true;
        }
        else if (action.equals("get")) {
            final String filename = args.getString(0);
            final Context ctx = cordova.getActivity().getApplicationContext();     
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        Context context = cordova.getActivity().getApplicationContext();
                        Intent intent = new Intent(context, XAPKDownloaderActivity.class);
                        intent.putExtras(bundle);
                        cordova.getActivity().startActivity(intent);
                        // Read file
                        PluginResult result = XAPKReader.readFile(ctx, filename, mainVersion, patchVersion, PluginResult.MESSAGE_TYPE_ARRAYBUFFER);
                        callbackContext.sendPluginResult(result);
                    }
                    catch(Exception e) {
                        e.printStackTrace();
                        callbackContext.error(e.getLocalizedMessage());
                    }
                }
            });
            return true;
        }
        else if(action.equals("getExpansionFileList")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        Context ctx = cordova.getActivity().getApplicationContext();
                        ZipResourceFile expansionFile = APKExpansionSupport.getAPKExpansionZipFile(ctx, mainVersion, patchVersion);

                        if (null == expansionFile) {
                            Log.e(LOG_TAG, "APKExpansionFile not found.");
                            return;
                        }

                        // Get all file entries
                        ZipResourceFile.ZipEntryRO[] entries = expansionFile.getAllEntries();

                        JSONArray jsonArray = new JSONArray();

                        for (ZipResourceFile.ZipEntryRO zipFileObject : entries) {
                            jsonArray.put(zipFileObject.mFileName);
                        }
                        
                        // Convert Hashmap to JSON Object
                        PluginResult result = new PluginResult(PluginResult.Status.OK, jsonArray);
                        callbackContext.sendPluginResult(result);
                    }
                    catch(Exception e) {
                        e.printStackTrace();
                        callbackContext.error(e.getLocalizedMessage());
                    }
                }
            });
            return true;
        }
        else if(action.equals("getPackageInfo")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    Activity appActivity = cordova.getActivity();
                    try {
                        PackageInfo pmInfo = appActivity.getPackageManager().getPackageInfo(appActivity.getPackageName(), 0);
                        String versionName = pmInfo.versionName;
                        int versionCode = pmInfo.versionCode;
                        JSONObject packageInfo = new JSONObject();
                        packageInfo.put("versionName", versionName);
                        packageInfo.put("versionCode", versionCode);
                        PluginResult result = new PluginResult(PluginResult.Status.OK, packageInfo);
                        callbackContext.sendPluginResult(result);
                    }
                    catch (PackageManager.NameNotFoundException e) {
                        callbackContext.error(e.getLocalizedMessage());
                    }
                    catch (JSONException e) {
                        callbackContext.error(e.getLocalizedMessage());
                    }
                }
            });
            return true;
        }
        else if(action.equals("writeExpansionFileList")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        Context ctx = cordova.getActivity().getApplicationContext();
                        ZipResourceFile expansionFile = APKExpansionSupport.getAPKExpansionZipFile(ctx, mainVersion, patchVersion);

                        if (null == expansionFile) {
                            Log.e(LOG_TAG, "APKExpansionFile not found.");
                            Activity appActivity = cordova.getActivity();
                            PackageInfo pmInfo = appActivity.getPackageManager().getPackageInfo(appActivity.getPackageName(), 0);
                            String versionName = pmInfo.versionName;
                            int versionCode = pmInfo.versionCode;
                            JSONObject packageInfo = new JSONObject();
                            packageInfo.put("versionName", versionName);
                            packageInfo.put("versionCode", versionCode);
                            PluginResult result = new PluginResult(PluginResult.Status.OK, packageInfo);
                            callbackContext.sendPluginResult(result);
                            return;
                        }

                        ZipResourceFile.ZipEntryRO[] allFiles = expansionFile.getAllEntries();
                        ArrayList list = new ArrayList();
                        for (ZipResourceFile.ZipEntryRO entry : allFiles) list.add(entry.mFileName);

                        String _filename = "EXPANSION_FILENAMES.json";
                        File file = new File(Environment.getExternalStorageDirectory(), _filename);
                        String _string = list.toString();
                        FileOutputStream outputStream = new FileOutputStream(file);
                        outputStream.write(_string.getBytes());
                        outputStream.close();

                        PluginResult result = new PluginResult(PluginResult.Status.OK, file.getAbsolutePath());
                        callbackContext.sendPluginResult(result);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            return true;
        }
        return false;
    }

    /**
     * Read file in APK Expansion file.
     *
     * @param ctx      The context of the main Activity.
     * @param filename The filename to read
     * @return         PluginResult
     */
    private static PluginResult readFile(Context ctx, String filename, int mainVersion, int patchVersion, final int resultType) throws IOException {
        // Get APKExpensionFile
        ZipResourceFile expansionFile = APKExpansionSupport.getAPKExpansionZipFile(ctx, mainVersion, patchVersion);

        if (null == expansionFile) {
            Log.e(LOG_TAG, "APKExpansionFile not found.");
            return null;
        }

        // Find file in ExpansionFile
        String fileName = Helpers.getExpansionAPKFileName(ctx, true, mainVersion);
        fileName = fileName.substring(0, fileName.lastIndexOf("."));
        AssetFileDescriptor fileDescriptor = expansionFile.getAssetFileDescriptor(fileName + "/" + filename);

        if (null == fileDescriptor) {
			fileDescriptor = expansionFile.getAssetFileDescriptor(filename);
			if (null == fileDescriptor) {
				Log.e(LOG_TAG, "File not found (" + filename + ").");
                return null;
            }
        }

        // Read file
        InputStream inputStream = fileDescriptor.createInputStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read = 0;
        while ((read = inputStream.read(buffer, 0, buffer.length)) != -1) {
            os.write(buffer, 0, read);
        }
        os.flush();

        // get file content type
        String contentType = URLConnection.guessContentTypeFromStream(inputStream);

        PluginResult result;
        switch (resultType) {
            case PluginResult.MESSAGE_TYPE_STRING:
                result = new PluginResult(PluginResult.Status.OK, os.toString("UTF-8"));
                break;
            case PluginResult.MESSAGE_TYPE_ARRAYBUFFER:
                result = new PluginResult(PluginResult.Status.OK, os.toByteArray());
                break;
            case PluginResult.MESSAGE_TYPE_BINARYSTRING:
                result = new PluginResult(PluginResult.Status.OK, os.toByteArray(), true);
                break;
            default: // Base64.
                byte[] base64 = Base64.encode(os.toByteArray(), Base64.NO_WRAP);
                String s = "data:" + contentType + ";base64," + new String(base64, "US-ASCII");
                result = new PluginResult(PluginResult.Status.OK, s);
        }

        return result;
    }

}
