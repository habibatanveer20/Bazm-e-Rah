package com.example.bazmeraah.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;

public class CurrencyEngine {

    private static final String TAG = "CURRENCY_ENGINE";
    private static final String SNAPSHOT_URL = "http://192.168.4.1:5000/snapshot";
    private static final String MODEL_NAME = "best_currency_model_float32.tflite";
    private static final String LABEL_FILE = "currency_labels.txt";
    private static final String PREFS_NAME = "AppSettings";
    private static final String KEY_LANGUAGE_URDU = "language_urdu";
    private static final float CONF_THRESHOLD = 0.40f;
    private static final float NMS_THRESHOLD = 0.50f;

    private final Context context;
    private boolean isUrdu = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Interpreter tflite;
    private List<String> labels = new ArrayList<>();

    private int inputWidth, inputHeight, inputChannels;
    private int dim1, dim2;
    private boolean transposedOutput = false;

    public interface DetectionCallback {
        void onResult(String result);
        void onError();
    }

    public CurrencyEngine(Context context) {
        this.context = context;
        SharedPreferences prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        isUrdu = prefs.getBoolean("language_urdu", false);
    }

    public void start() {
        try {
            MappedByteBuffer modelBuffer = loadModelFile(MODEL_NAME);

            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            tflite = new Interpreter(modelBuffer, options);

            Tensor inputTensor = tflite.getInputTensor(0);
            int[] inShape = inputTensor.shape();
            inputHeight = inShape[1];
            inputWidth = inShape[2];
            inputChannels = inShape[3];

            Tensor outputTensor = tflite.getOutputTensor(0);
            int[] outShape = outputTensor.shape();

            dim1 = outShape[1];
            dim2 = outShape[2];

            if (dim1 < dim2) transposedOutput = true;

            loadLabels();

        } catch (Exception e) {
            Log.e(TAG, "Model load failed", e);
        }
    }

    private void loadLabels() {
        try {
            InputStream is = context.getAssets().open(LABEL_FILE);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null)
                labels.add(line.trim());
            reader.close();
        } catch (Exception e) {
            Log.e(TAG, "Label load failed", e);
        }
    }

    private MappedByteBuffer loadModelFile(String modelName) throws Exception {
        AssetFileDescriptor fd = context.getAssets().openFd(modelName);
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        FileChannel fileChannel = fis.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY,
                fd.getStartOffset(),
                fd.getDeclaredLength());
    }

    public void fetchSnapshotAndDetect(DetectionCallback callback) {

        new Thread(() -> {
            try {
                URL url = new URL(SNAPSHOT_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                InputStream is = conn.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                is.close();
                conn.disconnect();

                if (bitmap == null) {
                    mainHandler.post(callback::onError);
                    return;
                }

                String result = runDetection(bitmap);

                mainHandler.post(() -> callback.onResult(result));

            } catch (Exception e) {
                Log.e(TAG, "Snapshot failed", e);
                mainHandler.post(callback::onError);
            }
        }).start();
    }

    private String runDetection(Bitmap bitmap) {

        ByteBuffer input = letterbox(bitmap);

        float[][][] output = new float[1][dim1][dim2];
        tflite.run(input, output);

        return decodeYOLO(output);
    }

    private String decodeYOLO(float[][][] output) {

        List<Detection> detections = new ArrayList<>();

        int boxes = transposedOutput ? dim2 : dim1;
        int elements = transposedOutput ? dim1 : dim2;

        for (int i = 0; i < boxes; i++) {

            float[] row = new float[elements];

            for (int j = 0; j < elements; j++) {
                row[j] = transposedOutput ?
                        output[0][j][i] :
                        output[0][i][j];
            }

            float x = row[0];
            float y = row[1];
            float w = row[2];
            float h = row[3];

            int bestClass = -1;
            float bestScore = 0f;

            for (int c = 4; c < elements; c++) {
                if (row[c] > bestScore) {
                    bestScore = row[c];
                    bestClass = c - 4;
                }
            }

            if (bestScore > CONF_THRESHOLD) {
                detections.add(new Detection(x, y, w, h, bestScore, bestClass));
            }
        }

        List<Detection> finalDetections = applyNMS(detections);
        if (finalDetections.isEmpty())
            return isUrdu ? "کرنسی شناخت نہیں ہو سکی" : "Currency not detected";
        Detection best = finalDetections.get(0);

        if (best.classId >= 0 && best.classId < labels.size()) {

            String label = labels.get(best.classId);

            return isUrdu
                    ? "یہ " + label + " کا نوٹ ہے"
                    : "This is a " + label + " rupee note";

        }

        return "Currency detected";
    }

    private List<Detection> applyNMS(List<Detection> detections) {

        detections.sort((a, b) ->
                Float.compare(b.confidence, a.confidence));

        List<Detection> result = new ArrayList<>();

        for (Detection d : detections) {

            boolean keep = true;

            for (Detection r : result) {
                if (iou(d, r) > NMS_THRESHOLD) {
                    keep = false;
                    break;
                }
            }

            if (keep) result.add(d);
        }

        return result;
    }

    private float iou(Detection a, Detection b) {

        float left = Math.max(a.x - a.w / 2, b.x - b.w / 2);
        float right = Math.min(a.x + a.w / 2, b.x + b.w / 2);
        float top = Math.max(a.y - a.h / 2, b.y - b.h / 2);
        float bottom = Math.min(a.y + a.h / 2, b.y + b.h / 2);

        float inter = Math.max(0, right - left) *
                Math.max(0, bottom - top);

        float union = a.w * a.h + b.w * b.h - inter + 1e-6f;

        return inter / union;
    }

    private ByteBuffer letterbox(Bitmap bitmap) {

        float scale = Math.min(
                (float) inputWidth / bitmap.getWidth(),
                (float) inputHeight / bitmap.getHeight());

        int newW = Math.round(bitmap.getWidth() * scale);
        int newH = Math.round(bitmap.getHeight() * scale);

        Bitmap resized = Bitmap.createScaledBitmap(bitmap, newW, newH, true);

        Bitmap outputBitmap = Bitmap.createBitmap(
                inputWidth, inputHeight,
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(outputBitmap);
        canvas.drawColor(Color.BLACK);

        int dx = (inputWidth - newW) / 2;
        int dy = (inputHeight - newH) / 2;

        canvas.drawBitmap(resized, dx, dy, null);

        ByteBuffer buffer = ByteBuffer.allocateDirect(
                inputWidth * inputHeight * inputChannels * 4);

        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[inputWidth * inputHeight];
        outputBitmap.getPixels(pixels, 0, inputWidth,
                0, 0, inputWidth, inputHeight);

        for (int pixel : pixels) {
            buffer.putFloat(((pixel >> 16) & 0xFF) / 255f);
            buffer.putFloat(((pixel >> 8) & 0xFF) / 255f);
            buffer.putFloat((pixel & 0xFF) / 255f);
        }

        buffer.rewind();
        return buffer;
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
    }

    private static class Detection {
        float x, y, w, h;
        float confidence;
        int classId;

        Detection(float x, float y, float w,
                  float h, float conf, int id) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.confidence = conf;
            this.classId = id;
        }
    }
}