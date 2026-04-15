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
    private static final String SNAPSHOT_URL = "http://192.168.4.1:5000/snapshot";
    private static final String MODEL_NAME = "yolov8n-oiv7_float32.tflite";
    private static final String LABEL_FILE = "openimages_labels.txt";
    private String pendingLabel = "";
    private int pendingCount = 0;

    private static final float CONF_THRESHOLD = 0.20f;
    private static final float NMS_THRESHOLD = 0.35f; // Slightly tighter for better accuracy
    private static final int MAX_DETECTIONS_FOR_NMS = 50; // Speed optimization

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Interpreter tflite;
    private List<String> labels = new ArrayList<>();


    // 🔥 YAHAN ADD KARO
    private Set<String> allowed = new HashSet<>(Arrays.asList(
            "Person","Man","Woman","Door",
            "Chair","Table","Desk","Couch","Shelf","Cabinetry",
            "Drawer","Wardrobe","Stool","Bench","Coffee table",
            "Laptop","Computer monitor","Computer keyboard",
            "Computer mouse","Mobile phone","Telephone","Printer",
            "Television","Tablet computer","Remote control",
            "Book","Notebook","Paper","File","Pen","Pencil",
            "Stapler","Calculator","Envelope",
            "Cup","Mug","Glass","Bottle","Plate","Bowl",
            "Spoon","Fork","Knife","Tray",
            "Door","Window","Curtain","Carpet","Rug","Mirror",
            "Clock","Wall clock","Fan","Heater","Lamp","Light bulb",
            "Light switch","Power plugs and sockets",
            "Backpack","Bag","Box","Waste container","Vase",
            "Plant","Flower","Picture frame","Whiteboard",
            "Stairs","Elevator","Escalator","Door handle",
            "Tree","Grass","Building","Wall","Fence","Pole","Street light",
            "Car","Bus","Truck","Motorcycle","Bicycle","Van",
            "Helmet","Umbrella","Traffic light","Traffic sign"
    ));

    private int inputWidth, inputHeight, inputChannels;
    private int dim1, dim2;

    private boolean transposedOutput = false;
    private boolean builtInNMS = false;

    // Stability & History
    private Detection lastDetectedObject = null;
    private Bitmap lastFrameBitmap = null;
    private String lastLabel = "";

    public interface DetectionCallback {
        void onResult(String spokenText);
        void onError();
    }

    public VisionEngine(Context context) {
        this.context = context;
    }

    public void start() {
        try {
            MappedByteBuffer modelBuffer = loadModelFile(MODEL_NAME);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4); // Keep 4 threads for balance

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

            if (dim2 == 6) {
                builtInNMS = true;
            } else if (dim1 < dim2) {
                transposedOutput = true;
            }

            loadLabels();
            Log.d(TAG, "Model & Labels loaded successfully");

        } catch (Exception e) {
            Log.e(TAG, "Model load failed", e);
        }
    }

    private void loadLabels() {
        try (InputStream is = context.getAssets().open(LABEL_FILE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) labels.add(line.trim());
        } catch (Exception e) {
            Log.e(TAG, "Label load failed", e);
        }
    }

    private MappedByteBuffer loadModelFile(String modelName) throws Exception {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
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
            ByteBuffer input = letterbox(bitmap);
            float[][][] output = new float[1][dim1][dim2];
            tflite.run(input, output);
            return decodeRawYOLO(output, bitmap);
        } catch (Exception e) {
            Log.e(TAG, "Inference error", e);
            return "Detection error";
        }
    }

    private String decodeRawYOLO(float[][][] output, Bitmap bitmap) {

        List<Detection> detections = new ArrayList<>();
        int boxes = transposedOutput ? dim2 : dim1;
        int elements = transposedOutput ? dim1 : dim2;

        for (int i = 0; i < boxes; i++) {

            float bestScore = 0f;
            int bestClass = -1;

            // 🔍 find best class
            for (int c = 4; c < elements; c++) {
                float score = transposedOutput ? output[0][c][i] : output[0][i][c];
                if (score > bestScore) {
                    bestScore = score;
                    bestClass = c - 4;
                }
            }

            // 🔥 label
            String label = (bestClass >= 0 && bestClass < labels.size())
                    ? labels.get(bestClass)
                    : "object";

            // 🔥 FILTER
            if (!allowed.contains(label)) {
                if (bestScore < 0.40f) continue;
            }

            float dynamicThreshold = 0.22f; // simple rakha (no bias)

            if (bestScore > dynamicThreshold) {

                float x = transposedOutput ? output[0][0][i] : output[0][i][0];
                float y = transposedOutput ? output[0][1][i] : output[0][i][1];
                float w = transposedOutput ? output[0][2][i] : output[0][i][2];
                float h = transposedOutput ? output[0][3][i] : output[0][i][3];

                if (w * h < 0.01f) continue;

                detections.add(new Detection(x, y, w, h, bestScore, bestClass));
            }
        }

        // ✅ NMS
        List<Detection> finalDetections = applyNMS(detections);

        if (finalDetections.isEmpty()) {
            lastLabel = "";
            lastDetectedObject = null;
            return "No object detected";
        }

        // 🎯 best detection
        Detection currentBest = finalDetections.get(0);
        int cls = currentBest.classId;

        String currentLabel = (cls >= 0 && cls < labels.size())
                ? labels.get(cls)
                : "object";

        // ✅ SIMPLE MEMORY (NO BIAS)
        lastDetectedObject = currentBest;
        lastFrameBitmap = bitmap;
        lastLabel = currentLabel;

        // ✅ SMART SPEAKING
        if (currentBest.confidence < 0.19f) {
            return "I think it might be a " + currentLabel;
        } else {
            return "I see a " + currentLabel;
        }
    }

    // Color detection method remains unchanged as it's functional
    public String detectColorOfLastObject() {

        if (lastDetectedObject == null || lastFrameBitmap == null)
            return "No object selected for color detection";

        int imgW = lastFrameBitmap.getWidth();
        int imgH = lastFrameBitmap.getHeight();

        float scale = Math.min((float) inputWidth / imgW, (float) inputHeight / imgH);
        int padX = (inputWidth - Math.round(imgW * scale)) / 2;
        int padY = (inputHeight - Math.round(imgH * scale)) / 2;

        float cx = (lastDetectedObject.x * inputWidth - padX) / scale;
        float cy = (lastDetectedObject.y * inputHeight - padY) / scale;
        float bw = (lastDetectedObject.w * inputWidth) / scale;
        float bh = (lastDetectedObject.h * inputHeight) / scale;

        int x = Math.max(0, (int)(cx - bw / 2));
        int y = Math.max(0, (int)(cy - bh / 2));
        int w = Math.min(imgW - x, (int)bw);
        int h = Math.min(imgH - y, (int)bh);

        if (w <= 0 || h <= 0)
            return "Unable to determine color";

        try {

            // 🎯 CENTER CROP (MAIN FIX)
            int centerX = x + w / 2;
            int centerY = y + h / 2;

            int cropW = w / 3;
            int cropH = h / 3;

            int startX = Math.max(0, centerX - cropW / 2);
            int startY = Math.max(0, centerY - cropH / 2);

            cropW = Math.min(cropW, imgW - startX);
            cropH = Math.min(cropH, imgH - startY);

            Bitmap cropped = Bitmap.createBitmap(
                    lastFrameBitmap,
                    startX,
                    startY,
                    cropW,
                    cropH
            );

            String color = getDominantColor(cropped);

            return "The color of the " + lastLabel + " is " + color;

        } catch (Exception e) {
            return "Color detection failed";
        }
    }

    private String getDominantColor(Bitmap bitmap) {

        Bitmap small = Bitmap.createScaledBitmap(bitmap, 40, 40, true);

        Map<Integer, Integer> colorMap = new HashMap<>();

        int[] pixels = new int[40 * 40];
        small.getPixels(pixels, 0, 40, 0, 0, 40, 40);

        for (int pixel : pixels) {

            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            // 🎯 quantization (noise reduce)
            int key = ((r / 32) << 16) | ((g / 32) << 8) | (b / 32);

            colorMap.put(key, colorMap.getOrDefault(key, 0) + 1);
        }

        // 🎯 top 2 colors
        int firstColor = 0, secondColor = 0;
        int firstCount = 0, secondCount = 0;

        for (Map.Entry<Integer, Integer> entry : colorMap.entrySet()) {

            int count = entry.getValue();

            if (count > firstCount) {
                secondCount = firstCount;
                secondColor = firstColor;

                firstCount = count;
                firstColor = entry.getKey();

            } else if (count > secondCount) {
                secondCount = count;
                secondColor = entry.getKey();
            }
        }

        // 🔁 convert FIRST color (FIXED)
        int r1 = ((firstColor >> 16) & 0xFF) * 32 + 16;
        int g1 = ((firstColor >> 8) & 0xFF) * 32 + 16;
        int b1 = (firstColor & 0xFF) * 32 + 16;

        float[] hsv1 = new float[3];
        Color.RGBToHSV(r1, g1, b1, hsv1);

        // 🔆 brightness adjust
        hsv1[2] = Math.min(hsv1[2] * 1.1f, 1.0f);

        String color1 = mapHSVToColor(hsv1[0], hsv1[1], hsv1[2]);

        // 🔁 convert SECOND color (FIXED)
        int r2 = ((secondColor >> 16) & 0xFF) * 32 + 16;
        int g2 = ((secondColor >> 8) & 0xFF) * 32 + 16;
        int b2 = (secondColor & 0xFF) * 32 + 16;

        float[] hsv2 = new float[3];
        Color.RGBToHSV(r2, g2, b2, hsv2);

        String color2 = mapHSVToColor(hsv2[0], hsv2[1], hsv2[2]);

        // 🎯 DECISION LOGIC

        // agar dominant gray hai lekin second strong hai → second use karo
        if (color1.equals("Gray") && secondCount > firstCount * 0.6f) {
            return color2;
        }

        return color1;
    }

    private String mapHSVToColor(float hue, float sat, float val) {

        // 🔆 brightness boost (camera weak case)
        val = Math.min(val * 1.3f, 1.0f);

        if (val < 0.08) return "Black";
        if (sat < 0.12 && val > 0.85) return "White";
        if (sat < 0.25) return "Gray";

        if (hue < 10 || hue > 350) return "Red";
        if (hue < 45) return (sat < 0.5) ? "Brown" : "Orange";
        if (hue < 70) return "Yellow";
        if (hue < 160) return "Green";
        if (hue < 250) return "Blue";
        if (hue < 300) return "Purple";

        return "Pink";
    }

    private List<Detection> applyNMS(List<Detection> detections) {
        // Sort by confidence descending
        Collections.sort(detections, (a, b) -> Float.compare(b.confidence, a.confidence));

        // Speed optimization: Only process top 100 boxes
        int limit = Math.min(detections.size(), MAX_DETECTIONS_FOR_NMS);
        List<Detection> topDetections = detections.subList(0, limit);

        List<Detection> result = new ArrayList<>();
        for (Detection d : topDetections) {
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
        float x1 = Math.max(a.x - a.w / 2, b.x - b.w / 2);
        float y1 = Math.max(a.y - a.h / 2, b.y - b.h / 2);
        float x2 = Math.min(a.x + a.w / 2, b.x + b.w / 2);
        float y2 = Math.min(a.y + a.h / 2, b.y + b.h / 2);
        float inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        float union = a.w * a.h + b.w * b.h - inter + 1e-6f;
        return inter / union;
    }

    private ByteBuffer letterbox(Bitmap bitmap) {
        float scale = Math.min((float) inputWidth / bitmap.getWidth(), (float) inputHeight / bitmap.getHeight());
        int newW = Math.round(bitmap.getWidth() * scale);
        int newH = Math.round(bitmap.getHeight() * scale);

        Bitmap resized = Bitmap.createScaledBitmap(bitmap, newW, newH, true);
        Bitmap outputBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(outputBitmap);

        // FIX: Using Neutral Gray (114, 114, 114) instead of Black for padding
        canvas.drawColor(Color.rgb(114, 114, 114));
        canvas.drawBitmap(resized, (inputWidth - newW) / 2f, (inputHeight - newH) / 2f, null);

        ByteBuffer buffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * inputChannels * 4);
        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[inputWidth * inputHeight];
        outputBitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        for (int pixel : pixels) {
            buffer.putFloat(((pixel >> 16) & 0xFF) / 255f);
            buffer.putFloat(((pixel >> 8) & 0xFF) / 255f);
            buffer.putFloat((pixel & 0xFF) / 255f);
        }
        buffer.rewind();
        return buffer;
    }

    private static class Detection {
        float x, y, w, h, confidence;
        int classId;
        Detection(float x, float y, float w, float h, float conf, int id) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.confidence = conf; this.classId = id;
        }
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
    }
}