package com.example.bazmeraah;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.*;
import android.widget.*;

import com.example.bazmeraah.ai.FaceDatabase;

import java.util.*;

public class peopleFragment extends Fragment {

    private boolean allowTTS = false;

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private ToneGenerator toneGen;

    private boolean isUrdu = false;

    private Map<String, float[]> peopleMap;

    private String lastName = null;
    private String pendingName = null;

    private String currentState = "MAIN";

    private boolean isListening = false;
    private boolean isSpeaking = false;

    private Handler handler = new Handler(Looper.getMainLooper());

    public peopleFragment() {}

    public peopleFragment(boolean allowTTS) {
        this.allowTTS = allowTTS;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.activity_memory_people, container, false);

        isUrdu = requireContext()
                .getSharedPreferences("AppSettings", 0)
                .getBoolean("language_urdu", false);

        FaceDatabase db = new FaceDatabase(requireContext());
        peopleMap = db.getAllFaces();

        setupList(view);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
        speechRecognizer.setRecognitionListener(listener);

        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                isUrdu ? new Locale("ur","PK") : Locale.getDefault());

        tts = new TextToSpeech(getContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(isUrdu ? new Locale("ur","PK") : Locale.getDefault());
                if (allowTTS) askUser();
            }
        });

        return view;
    }

    // ================= LIST =================

    private void setupList(View view) {

        ListView listView = view.findViewById(R.id.lv_people);
        View emptyView = view.findViewById(R.id.empty_people);

        List<String> nameList = new ArrayList<>(peopleMap.keySet());

        if (nameList.size() > 20) nameList = nameList.subList(0, 20);

        if (nameList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }

        List<String> finalList = nameList;

        listView.setAdapter(new BaseAdapter() {

            @Override public int getCount() { return finalList.size(); }
            @Override public Object getItem(int position) { return finalList.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {

                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_person, parent, false);
                }

                TextView txtName = convertView.findViewById(R.id.txtName);
                Button btnDelete = convertView.findViewById(R.id.btnDelete);

                String name = finalList.get(position);
                txtName.setText((position + 1) + ". " + name);

                btnDelete.setOnClickListener(v -> deletePerson(name));

                return convertView;
            }
        });
    }

    // ================= AI FLOW =================

    private void askUser() {
        currentState = "MAIN";
        speak(isUrdu
                        ? "آپ کیا کرنا چاہتی ہیں؟ لسٹ سننی ہے یا کسی کو سرچ کرنا ہے؟"
                        : "What do you want to do? List or search.",
                this::startListening);
    }

    private void askName() {
        currentState = "SEARCH";
        speak(isUrdu ? "نام بتائیں" : "Say the name",
                this::startListening);
    }

    private void askNextAction() {
        currentState = "NEXT";
        speak(isUrdu
                        ? "اب کیا کرنا چاہتی ہیں؟ لسٹ، سرچ یا مین پیج؟"
                        : "What do you want to do next? List, search or main page?",
                this::startListening);
    }

    private void retry() {
        handler.postDelayed(() ->
                speak(isUrdu ? "دوبارہ بولیں" : "Say again",
                        this::startListening), 800);
    }

    // ================= MIC =================

    private void startListening() {

        if (isListening || isSpeaking) return;

        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }

        try {
            if (isListening) speechRecognizer.cancel();
        } catch (Exception ignored) {}

        isListening = true;

        speechRecognizer.startListening(speechIntent);

        if (!isSpeaking) {
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
        }
    }

    // ================= VOICE =================

    private final RecognitionListener listener = new RecognitionListener() {

        @Override
        public void onResults(Bundle results) {

            isListening = false;

            ArrayList<String> matches =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            if (matches == null || matches.isEmpty()) {
                retry();
                return;
            }

            String heard = matches.get(0).toLowerCase();
            Log.d("VOICE", heard);

            if (heard.contains("main") || heard.contains("home") || heard.contains("مین")) {
                startActivity(new Intent(getActivity(), MainActivity.class));
                getActivity().finish();
                return;
            }

            switch (currentState) {

                case "MAIN":

                    if (heard.contains("list") || heard.contains("لوگ")) {
                        speakPeople();
                    }
                    else if (heard.contains("search") || heard.contains("تلاش")) {
                        askName();
                    }
                    else {
                        retry();
                    }
                    break;

                case "SEARCH":
                    handleSearch(heard);
                    break;

                case "CONFIRM_NAME":

                    if (heard.contains("yes") || heard.contains("haan") || heard.contains("جی")) {

                        lastName = pendingName;

                        speak(isUrdu
                                        ? "کیا آپ واقعی " + lastName + " کو ڈیلیٹ کرنا چاہتی ہیں؟"
                                        : "Are you sure you want to delete " + lastName + "?",
                                () -> {
                                    currentState = "DELETE_CONFIRM";
                                    startListening();
                                });

                    } else {
                        askName();
                    }
                    break;

                case "DELETE_CONFIRM":

                    if (heard.contains("yes") || heard.contains("haan") || heard.contains("جی")) {
                        deletePerson(lastName);
                    }
                    else if (heard.contains("no") || heard.contains("نہیں")) {
                        askNextAction();
                    }
                    else {
                        retry();
                    }
                    break;

                case "NEXT":

                    if (heard.contains("search") || heard.contains("تلاش")) {
                        askName();
                    }
                    else if (heard.contains("list") || heard.contains("لوگ")) {
                        speakPeople();
                    }
                    else if (heard.contains("main") || heard.contains("home") || heard.contains("مین")) {
                        startActivity(new Intent(getActivity(), MainActivity.class));
                        getActivity().finish();
                    }
                    else {
                        askNextAction();
                    }
                    break;
            }
        }

        @Override public void onEndOfSpeech() { isListening = false; }

        @Override
        public void onError(int error) {
            isListening = false;

            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                retry();
            } else {
                Log.e("VOICE_ERROR", "Error: " + error);
            }
        }

        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {}
    };

    // ================= LOGIC =================

    private void handleSearch(String heard) {

        String foundName = null;

        for (String name : peopleMap.keySet()) {

            if (heard.contains(name.toLowerCase()) || name.toLowerCase().contains(heard)) {
                foundName = name;
                break;
            }
        }

        if (foundName != null) {

            lastName = foundName;

            speak(isUrdu
                            ? "شخص مل گیا " + foundName + ". کیا آپ اسے ڈیلیٹ کرنا چاہتی ہیں؟"
                            : "Person found " + foundName + ". Do you want to delete?",
                    () -> {
                        currentState = "DELETE_CONFIRM";
                        startListening();
                    });

        } else {

            speak(isUrdu
                            ? "شخص نہیں ملا۔ دوبارہ سرچ کریں یا مین پیج پر جائیں"
                            : "Person not found. Search again or go to main page",
                    () -> {
                        currentState = "NEXT";
                        startListening();
                    });
        }
    }
    private void speakPeople() {

        currentState = "LIST";

        if (peopleMap.isEmpty()) {
            speak(isUrdu ? "کوئی لوگ موجود نہیں" : "No people stored",
                    this::askNextAction);
            return;
        }

        StringBuilder names = new StringBuilder();
        for (String n : peopleMap.keySet()) {
            names.append(n).append(", ");
        }

        speak(isUrdu ? "یہ لوگ ہیں: " + names : "People: " + names,
                this::askNextAction);
    }

    private void deletePerson(String name) {

        FaceDatabase db = new FaceDatabase(requireContext());
        db.deleteFace(name);

        peopleMap.remove(name);
        setupList(getView());

        speak(isUrdu ? name + " ڈیلیٹ ہو گیا" : name + " deleted",
                this::askNextAction);
    }

    // ================= TTS =================

    private void speak(String text, Runnable next) {

        isSpeaking = true;

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ID");

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {

            @Override public void onStart(String id) {}

            @Override
            public void onDone(String id) {
                isSpeaking = false;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(next);
                }
            }

            @Override public void onError(String id) {
                isSpeaking = false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (tts != null) tts.shutdown();
    }
}