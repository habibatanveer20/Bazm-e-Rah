package com.example.bazmeraah.ai;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;

public class FaceEngine {

    private static final String TAG = "FACE_ENGINE";
    private static final String MODEL_NAME = "facenet.tflite";
    private static final String SNAPSHOT_URL = "http://192.168.1.14:8080/snapshot";

    private Context context;
    private Interpreter interpreter;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private int inputSize = 160;
    private int embeddingSize = 128;

    private FaceDetector detector;

    public interface FaceCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    /* ================= CONSTRUCTOR ================= */

    public FaceEngine(Context context) {

        this.context = context;

        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .enableTracking()
                        .build();

        detector = FaceDetection.getClient(options);
    }

    /* ================= START (MODEL LOAD) ================= */

    public void start() {
        try {

            MappedByteBuffer modelBuffer = loadModelFile(context);
            interpreter = new Interpreter(modelBuffer);

            Tensor inputTensor = interpreter.getInputTensor(0);
            inputSize = inputTensor.shape()[1];

            Tensor outputTensor = interpreter.getOutputTensor(0);
            embeddingSize = outputTensor.shape()[1];

            Log.d(TAG, "Face model loaded");

        } catch (Exception e) {
            Log.e(TAG, "Model load failed", e);
        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws Exception {
        AssetFileDescriptor fd = context.getAssets().openFd(MODEL_NAME);
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        FileChannel channel = fis.getChannel();
        return channel.map(FileChannel.MapMode.READ_ONLY,
                fd.getStartOffset(),
                fd.getDeclaredLength());
    }

    /* ================= SNAPSHOT ================= */

    private Bitmap fetchSnapshot() {
        try {
            URL url = new URL(SNAPSHOT_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.connect();
            InputStream is = conn.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            conn.disconnect();
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Snapshot failed", e);
            return null;
        }
    }

    /* ================= MAIN PIPELINE ================= */

    private void processFace(Bitmap bitmap,
                             FaceDatabase database,
                             FaceCallback callback,
                             boolean isSave,
                             String saveName) {

        InputImage image = InputImage.fromBitmap(bitmap, 0);

        detector.process(image)
                .addOnSuccessListener(faces -> {

                    if (faces.isEmpty()) {
                        mainHandler.post(() ->
                                callback.onError("No face detected"));
                        return;
                    }

                    Face face = faces.get(0);
                    Rect box = face.getBoundingBox();

                    Bitmap cropped = Bitmap.createBitmap(
                            bitmap,
                            Math.max(box.left, 0),
                            Math.max(box.top, 0),
                            Math.min(box.width(), bitmap.getWidth() - box.left),
                            Math.min(box.height(), bitmap.getHeight() - box.top)
                    );

                    float[] embedding = getEmbedding(cropped);

                    if (embedding == null) {
                        mainHandler.post(() ->
                                callback.onError("Embedding failed"));
                        return;
                    }

                    if (isSave) {

                        database.saveFace(saveName, embedding);

                        mainHandler.post(() ->
                                callback.onSuccess("Face " + saveName + " saved"));

                    } else {

                        Map<String, float[]> db = database.getAllFaces();

                        if (db.isEmpty()) {
                            mainHandler.post(() ->
                                    callback.onSuccess("Database empty"));
                            return;
                        }

                        String name = findBestMatch(embedding, db);

                        if (name != null)
                            mainHandler.post(() ->
                                    callback.onSuccess("Ye " + name + " hai"));
                        else
                            mainHandler.post(() ->
                                    callback.onSuccess("Unknown face"));
                    }

                })
                .addOnFailureListener(e ->
                        mainHandler.post(() ->
                                callback.onError("Detection failed")));
    }

    /* ================= SAVE ================= */

    public void saveFace(String name,
                         FaceDatabase database,
                         FaceCallback callback) {

        new Thread(() -> {

            Bitmap bitmap = fetchSnapshot();

            if (bitmap == null) {
                mainHandler.post(() ->
                        callback.onError("Camera error"));
                return;
            }

            processFace(bitmap, database, callback, true, name);

        }).start();
    }

    /* ================= RECOGNIZE ================= */

    public void recognizeFace(FaceDatabase database,
                              FaceCallback callback) {

        new Thread(() -> {

            Bitmap bitmap = fetchSnapshot();

            if (bitmap == null) {
                mainHandler.post(() ->
                        callback.onError("Camera error"));
                return;
            }

            processFace(bitmap, database, callback, false, null);

        }).start();
    }

    /* ================= EMBEDDING ================= */

    private float[] getEmbedding(Bitmap faceBitmap) {

        if (interpreter == null)
            return null;

        Bitmap resized =
                Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true);

        ByteBuffer buffer =
                ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4);

        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[inputSize * inputSize];
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize);

        for (int pixel : pixels) {

            float r = ((pixel >> 16) & 0xFF);
            float g = ((pixel >> 8) & 0xFF);
            float b = (pixel & 0xFF);

            buffer.putFloat((r - 127.5f) / 128f);
            buffer.putFloat((g - 127.5f) / 128f);
            buffer.putFloat((b - 127.5f) / 128f);
        }

        buffer.rewind();

        float[][] output = new float[1][embeddingSize];
        interpreter.run(buffer, output);

        return normalize(output[0]);
    }

    private float[] normalize(float[] emb) {

        float sum = 0f;

        for (float v : emb)
            sum += v * v;

        float norm = (float) Math.sqrt(sum);

        if (norm == 0) return emb;

        for (int i = 0; i < emb.length; i++)
            emb[i] /= norm;

        return emb;
    }

    /* ================= MATCH ================= */

    private String findBestMatch(float[] newEmb,
                                 Map<String, float[]> database) {

        float bestDistance = Float.MAX_VALUE;
        String bestMatch = null;

        for (String name : database.keySet()) {

            float dist = calculateDistance(newEmb, database.get(name));

            if (dist < bestDistance) {
                bestDistance = dist;
                bestMatch = name;
            }
        }

        Log.d("FACE_DEBUG", "Best distance: " + bestDistance);

        return bestDistance < 0.9f ? bestMatch : null;
    }

    private float calculateDistance(float[] e1, float[] e2) {

        float sum = 0f;

        for (int i = 0; i < e1.length; i++) {
            float diff = e1[i] - e2[i];
            sum += diff * diff;
        }

        return (float) Math.sqrt(sum);
    }

    public void close() {
        if (interpreter != null)
            interpreter.close();
    }
}