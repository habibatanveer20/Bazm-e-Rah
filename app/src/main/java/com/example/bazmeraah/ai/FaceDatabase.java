package com.example.bazmeraah.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FaceDatabase {

    private static final String TAG = "FACE_DB";
    private static final String PREF_NAME = "FaceDB";
    private static final String KEY_DATA = "faces";

    private SharedPreferences prefs;

    public FaceDatabase(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /* ================= SAVE ================= */

    public void saveFace(String name, float[] embedding) {

        try {
            JSONObject allFaces = getAllFacesJSON();
            JSONArray array = new JSONArray();

            for (float v : embedding) {
                array.put(v);
            }

            allFaces.put(name, array);

            // 🔥 Use commit (synchronous save)
            boolean success = prefs.edit()
                    .putString(KEY_DATA, allFaces.toString())
                    .commit();

            Log.d(TAG, "Saved face: " + name);
            Log.d(TAG, "Commit success: " + success);
            debugPrintDatabase();

        } catch (Exception e) {
            Log.e(TAG, "Save error", e);
        }
    }

    /* ================= GET ALL ================= */

    public Map<String, float[]> getAllFaces() {

        Map<String, float[]> map = new HashMap<>();

        try {
            JSONObject allFaces = getAllFacesJSON();
            Iterator<String> keys = allFaces.keys();

            while (keys.hasNext()) {

                String name = keys.next();
                JSONArray arr = allFaces.getJSONArray(name);

                float[] embedding = new float[arr.length()];

                for (int i = 0; i < arr.length(); i++) {
                    embedding[i] = (float) arr.getDouble(i);
                }

                map.put(name, embedding);
            }

        } catch (Exception e) {
            Log.e(TAG, "Read error", e);
        }

        return map;
    }

    /* ================= DEBUG PRINT ================= */

    public void debugPrintDatabase() {

        Map<String, float[]> map = getAllFaces();

        Log.d(TAG, "Total faces: " + map.size());

        for (String name : map.keySet()) {
            Log.d(TAG, "Saved name: " + name);
        }
    }

    /* ================= CLEAR DB (Testing) ================= */

    public void clearDatabase() {

        prefs.edit().clear().commit();
        Log.d(TAG, "Database cleared");
    }

    /* ================= INTERNAL ================= */

    private JSONObject getAllFacesJSON() {

        try {
            String data = prefs.getString(KEY_DATA, "{}");
            return new JSONObject(data);
        } catch (Exception e) {
            Log.e(TAG, "JSON error", e);
            return new JSONObject();
        }
    }
    public void deleteFace(String name) {
        try {
            JSONObject allFaces = getAllFacesJSON();

            // remove specific person
            allFaces.remove(name);

            // save back
            prefs.edit()
                    .putString(KEY_DATA, allFaces.toString())
                    .commit();

            Log.d(TAG, "Deleted: " + name);

        } catch (Exception e) {
            Log.e(TAG, "Delete error", e);
        }
    }
}