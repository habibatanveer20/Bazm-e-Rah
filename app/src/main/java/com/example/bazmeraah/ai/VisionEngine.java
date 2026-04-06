package com.example.bazmeraah.ai;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;

public class VisionEngine {

    private static final String TAG = "VISION_ENGINE";

    private static final String SNAPSHOT_URL =
            "http://192.168.1.14:8080/snapshot";

    private static final String MODEL_NAME = "1.tflite";
    private static final String LABEL_FILE = "coco_labels.txt";

    private static final float CONF_THRESHOLD = 0.4f;

    private final Context context;
    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private Interpreter tflite;
    private List<String> labels = new ArrayList<>();

    private int inputWidth;
    private int inputHeight;
    private int inputChannels;

    private Detection lastDetectedObject = null;
    private Bitmap lastFrameBitmap = null;

    public interface DetectionCallback {
        void onResult(String spokenText);
        void onError();
    }

    public VisionEngine(Context context) {
        this.context = context;
    }

    public void start() {
        try {

            MappedByteBuffer modelBuffer =
                    loadModelFile(MODEL_NAME);

            Interpreter.Options options =
                    new Interpreter.Options();
            options.setNumThreads(4);

            tflite = new Interpreter(modelBuffer, options);

            Tensor inputTensor = tflite.getInputTensor(0);
            int[] inShape = inputTensor.shape();

            inputHeight = inShape[1];
            inputWidth = inShape[2];
            inputChannels = inShape[3];

            loadLabels();

        } catch (Exception e) {
            Log.e(TAG, "Model load failed", e);
        }
    }

    private void loadLabels() {
        try {
            InputStream is =
                    context.getAssets().open(LABEL_FILE);
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(is));

            String line;
            while ((line = reader.readLine()) != null)
                labels.add(line.trim());

            reader.close();

        } catch (Exception e) {
            Log.e(TAG, "Label load failed", e);
        }
    }

    private MappedByteBuffer loadModelFile(String modelName)
            throws Exception {

        AssetFileDescriptor fileDescriptor =
                context.getAssets().openFd(modelName);

        FileInputStream inputStream =
                new FileInputStream(fileDescriptor.getFileDescriptor());

        FileChannel fileChannel =
                inputStream.getChannel();

        return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.getStartOffset(),
                fileDescriptor.getDeclaredLength()
        );
    }

    public void fetchSnapshotAndDetect(
            DetectionCallback callback) {

        new Thread(() -> {
            try {

                URL url = new URL(SNAPSHOT_URL);
                HttpURLConnection conn =
                        (HttpURLConnection) url.openConnection();

                conn.connect();

                InputStream is = conn.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(is);

                is.close();
                conn.disconnect();

                if (bitmap == null) {
                    mainHandler.post(callback::onError);
                    return;
                }

                String result =
                        runObjectDetection(bitmap);

                mainHandler.post(
                        () -> callback.onResult(result));

            } catch (Exception e) {
                Log.e(TAG, "Snapshot failed", e);
                mainHandler.post(callback::onError);
            }
        }).start();
    }


    private String runObjectDetection(Bitmap bitmap) {

        try {

            ByteBuffer input = preprocess(bitmap);

            float[][][] boxes = new float[1][10][4];
            float[][] classes = new float[1][10];
            float[][] scores = new float[1][10];
            float[] num = new float[1];

            Object[] inputs = {input};

            Map<Integer, Object> outputs = new HashMap<>();
            outputs.put(0, boxes);
            outputs.put(1, classes);
            outputs.put(2, scores);
            outputs.put(3, num);

            tflite.runForMultipleInputsOutputs(inputs, outputs);

            List<Detection> detections = new ArrayList<>();

            for (int i = 0; i < 10; i++) {

                if (scores[0][i] > CONF_THRESHOLD) {

                    int cls = (int) classes[0][i];

                    float ymin = boxes[0][i][0];
                    float xmin = boxes[0][i][1];
                    float ymax = boxes[0][i][2];
                    float xmax = boxes[0][i][3];

                    detections.add(new Detection(
                            xmin, ymin,
                            xmax - xmin,
                            ymax - ymin,
                            scores[0][i],
                            cls
                    ));
                }
            }

            if (detections.isEmpty())
                return "Kuch nazar nahi aa raha";

            lastDetectedObject = detections.get(0);
            lastFrameBitmap = bitmap;

            return generateScene(detections);

        } catch (Exception e) {
            Log.e(TAG, "Inference error", e);
            return "Detection error";
        }
    }

    private String generateScene(List<Detection> detections) {

        List<String> parts = new ArrayList<>();
        Set<String> added = new HashSet<>();

        for (Detection d : detections) {

            if (d.classId < 0 || d.classId >= labels.size()) continue;

            String name = labels.get(d.classId);

            if (added.contains(name)) continue;
            added.add(name);

            String pos = d.x < 0.3 ? "baayi taraf" :
                    d.x > 0.7 ? "daayi taraf" : "samne";

            String dist = (d.w * d.h > 0.2) ? "qareeb" : "door";

            parts.add(name + " " + pos + " " + dist);
        }

        return "Samne " + String.join(", ", parts) + " hai.";
    }

    public String detectColorOfLastObject() {

        if (lastDetectedObject == null || lastFrameBitmap == null)
            return "Koi object select nahi hai";

        int imgW = lastFrameBitmap.getWidth();
        int imgH = lastFrameBitmap.getHeight();

        int x = (int)(lastDetectedObject.x * imgW);
        int y = (int)(lastDetectedObject.y * imgH);

        Bitmap cropped = Bitmap.createBitmap(
                lastFrameBitmap,
                Math.max(0, x - 20),
                Math.max(0, y - 20),
                40,
                40
        );

        int pixel = cropped.getPixel(20, 20);

        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;

        String color = (r > g && r > b) ? "Red" :
                (g > r && g > b) ? "Green" : "Blue";

        return labels.get(lastDetectedObject.classId) + " ka color " + color;
    }

    private ByteBuffer preprocess(Bitmap bitmap) {

        Bitmap resized = Bitmap.createScaledBitmap(
                bitmap, inputWidth, inputHeight, true);

        ByteBuffer buffer = ByteBuffer.allocateDirect(
                inputWidth * inputHeight * inputChannels * 4);

        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[inputWidth * inputHeight];
        resized.getPixels(pixels, 0, inputWidth, 0, 0,
                inputWidth, inputHeight);

        for (int pixel : pixels) {

            buffer.putFloat(((pixel >> 16) & 0xFF) / 255f);
            buffer.putFloat(((pixel >> 8) & 0xFF) / 255f);
            buffer.putFloat((pixel & 0xFF) / 255f);
        }

        buffer.rewind();
        return buffer;
    }

    private static class Detection {
        float x, y, w, h;
        float confidence;
        int classId;

        Detection(float x, float y,
                  float w, float h,
                  float conf, int id) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.confidence = conf;
            this.classId = id;
        }
    }
    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
    }
}