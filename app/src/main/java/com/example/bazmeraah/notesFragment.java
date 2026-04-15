package com.example.bazmeraah;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.*;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.*;
import android.widget.*;

import java.util.*;

public class notesFragment extends Fragment {

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private ToneGenerator toneGen;

    private boolean isListening = false;
    private boolean isSpeaking = false;
    private boolean isReadingNotes = false;
    private boolean isNavigating = false;
    private boolean isEnglish = true;

    private List<String> notesList = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private String currentState = "ASK";
    private int pendingEditIndex = -1;

    public notesFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.activity_memory_notes, container, false);

        loadNotes();
        setupList(view);

        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        setupSpeech();
        SharedPreferences prefs = requireContext().getSharedPreferences("AppSettings", 0);
        boolean isUrdu = prefs.getBoolean("language_urdu", false);
        isEnglish = !isUrdu;
        setupTTS();

        return view;
    }

    // ================= SETUP =================

    private void setupSpeech() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
        speechRecognizer.setRecognitionListener(listener);

        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
    }

    private void setupTTS() {

        tts = new TextToSpeech(getContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(isEnglish ? Locale.ENGLISH : new Locale("ur", "PK"));
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {

                    @Override public void onStart(String id) {}

                    @Override
                    public void onDone(String id) {

                        isSpeaking = false;

                        if ("NAV_MAIN".equals(id)) {
                            startActivity(new Intent(getActivity(), MainActivity.class));
                            getActivity().finish();
                            return;
                        }

                        if ("EXIT_APP".equals(id)) {
                            getActivity().finishAffinity();
                            return;
                        }

                        if (isReadingNotes) {
                            isReadingNotes = false;
                            currentState = "AFTER_ACTION";
                            speak(isEnglish
                                            ? "What do you want next? Read again, edit, delete, go to main page or exit?"
                                            : "اب آپ کیا کرنا چاہتے ہیں؟ دوبارہ پڑھیں، ایڈٹ کریں، ڈیلیٹ کریں، مین پیج پر جائیں یا ایگزٹ کریں؟"
                                    , "AFTER");
                        }

                        startListeningWithDelay();
                    }

                    @Override
                    public void onError(String id) {
                        isSpeaking = false;
                        startListeningWithDelay();
                    }
                });

                speak(isEnglish
                                ? "You are on the notes page. Do you want to read, edit or delete your notes?"
                                : "آپ نوٹس پیج پر ہیں۔ کیا آپ نوٹس پڑھنا، ایڈٹ کرنا یا ڈیلیٹ کرنا چاہتے ہیں؟"
                        , "ASK");
            }
        });
    }

    // ================= DATA =================

    private void loadNotes() {
        SharedPreferences prefs = requireContext().getSharedPreferences("NotesPrefs", 0);
        notesList = new ArrayList<>(prefs.getStringSet("notes", new HashSet<>()));
    }

    private void saveNotes() {
        SharedPreferences prefs = requireContext().getSharedPreferences("NotesPrefs", 0);
        prefs.edit().putStringSet("notes", new HashSet<>(notesList)).apply();
    }

    private void setupList(View view) {
        ListView listView = view.findViewById(R.id.lv_notes);

        adapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_list_item_1,
                notesList
        );

        listView.setAdapter(adapter);
    }

    // ================= MIC =================

    private void startListeningWithDelay() {
        if (getActivity() == null || isNavigating) return;
        new Handler(Looper.getMainLooper()).postDelayed(this::startListening, 700);
    }

    private void startListening() {

        if (isListening || isSpeaking || isNavigating) return;

        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }

        isListening = true;
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
        speechRecognizer.startListening(speechIntent);
    }

    // ================= VOICE =================

    private final RecognitionListener listener = new RecognitionListener() {

        @Override
        public void onResults(Bundle results) {

            isListening = false;

            ArrayList<String> matches =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            if (matches == null || matches.isEmpty()) {
                speak(isEnglish
                                ? "I didn't catch that, please repeat"
                                : "میں سمجھ نہیں سکی، دوبارہ کہیں"
                        , "REPEAT");
            }

            String heard = matches.get(0).toLowerCase().trim();
            Log.d("VOICE_DEBUG", "USER SAID: " + heard);

            // ================= STATE FIRST =================

            if (currentState.equals("EDIT_SELECT")) {

                int index = extractNumber(heard);

                if (index >= 1 && index <= notesList.size()) {
                    pendingEditIndex = index - 1;
                    currentState = "EDITING";
                    speak(isEnglish
                                    ? "What do you want to update?"
                                    : "آپ کیا اپڈیٹ کرنا چاہتے ہیں؟"
                            , "EDIT");
                } else {
                    speak(isEnglish
                                    ? "Please say a valid number like 1 or 2"
                                    : "براہ کرم درست نمبر کہیں جیسے 1 یا 2"
                            , "REPEAT");
                }
                return;
            }

            if (currentState.equals("DELETE_SELECT")) {

                int index = extractNumber(heard);

                if (index >= 1 && index <= notesList.size()) {
                    notesList.remove(index - 1);
                    saveNotes();
                    adapter.notifyDataSetChanged();

                    currentState = "AFTER_ACTION";
                    speak(isEnglish
                                    ? "Note deleted. What next?"
                                    : "نوٹ ڈیلیٹ ہو گیا۔ اب کیا کرنا ہے؟"
                            , "AFTER");
                } else {
                    speak(isEnglish
                                    ? "Please say a valid number like 1 or 2"
                                    : "براہ کرم درست نمبر کہیں جیسے 1 یا 2"
                            , "REPEAT");
                }
                return;
            }

            if (currentState.equals("EDITING")) {

                notesList.set(pendingEditIndex, heard);
                saveNotes();
                adapter.notifyDataSetChanged();

                currentState = "AFTER_ACTION";
                speak("Note updated. What next?", "AFTER");
                return;
            }

            // ================= COMMANDS =================

            if (heard.contains("edit") || heard.contains("update") || heard.contains("change")) {

                int index = extractNumber(heard);

                if (index == -1) {
                    currentState = "EDIT_SELECT";
                    speak(isEnglish
                                    ? "Which note number do you want to edit?"
                                    : "آپ کون سا نوٹ نمبر ایڈٹ کرنا چاہتے ہیں؟"
                            , "ASK_NUM");
                } else {
                    pendingEditIndex = index - 1;
                    currentState = "EDITING";
                    speak(isEnglish
                                    ? "Note updated. What next?"
                                    : "نوٹ اپڈیٹ ہو گیا۔ اب کیا کرنا ہے؟"
                            , "AFTER");
                }
                return;
            }

            if (heard.contains("delete") || heard.contains("remove")) {

                int index = extractNumber(heard);

                if (index == -1) {
                    currentState = "DELETE_SELECT";
                    speak(isEnglish
                                    ? "Which note number do you want to delete?"
                                    : "آپ کون سا نوٹ نمبر ڈیلیٹ کرنا چاہتے ہیں؟"
                            , "ASK_DEL");
                } else {
                    notesList.remove(index - 1);
                    saveNotes();
                    adapter.notifyDataSetChanged();

                    currentState = "AFTER_ACTION";
                    speak(isEnglish
                                    ? "Note deleted. What next?"
                                    : "نوٹ ڈیلیٹ ہو گیا۔ اب کیا کرنا ہے؟"
                            , "AFTER");
                }
                return;
            }

            // ================= MAIN FLOW =================

            if (currentState.equals("ASK") || currentState.equals("AFTER_ACTION")) {

                if (heard.contains("read") || heard.contains("again")) {
                    currentState = "READING";
                    speakNotes();
                }

                else if (heard.contains("main")) {
                    isNavigating = true;
                    speak(isEnglish
                                    ? "Going to main page"
                                    : "مین پیج پر جا رہی ہوں"
                            , "NAV_MAIN");
                }

                else if (heard.contains("exit")) {
                    isNavigating = true;
                    speak(isEnglish
                                    ? "Exiting app"
                                    : "ایپ بند کی جا رہی ہے"
                            , "EXIT_APP");
                }

                else {
                    speak(isEnglish
                                    ? "I didn't catch that, please repeat"
                                    : "میں سمجھ نہیں سکی، دوبارہ کہیں"
                            , "REPEAT");
                }
            }
        }

        @Override public void onEndOfSpeech() { isListening = false; }

        @Override
        public void onError(int error) {
            isListening = false;
            speak(isEnglish
                            ? "I didn't catch that, please repeat"
                            : "میں سمجھ نہیں سکی، دوبارہ کہیں"
                    , "REPEAT");
        }

        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {}
    };

    // ================= NOTES =================

    private void speakNotes() {

        if (notesList.isEmpty()) {
            speak(isEnglish
                            ? "You have no notes"
                            : "آپ کے پاس کوئی نوٹس نہیں ہیں"
                    , "EMPTY");
            return;
        }

        isReadingNotes = true;

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < notesList.size(); i++) {
            sb.append(isEnglish ? "Note number " : "نوٹ نمبر ")
                    .append(i + 1)
                    .append(": ")
                    .append(notesList.get(i))
                    .append(". ");
        }

        speak(sb.toString(), "READ");
    }

    // ================= NUMBER =================

    private int extractNumber(String text) {

        for (String word : text.split(" ")) {
            try {
                return Integer.parseInt(word);
            } catch (Exception ignored) {}
        }

        if (text.contains("one")) return 1;
        if (text.contains("two") || text.contains("to") || text.contains("too")) return 2;
        if (text.contains("three")) return 3;
        if (text.contains("four")) return 4;
        if (text.contains("five")) return 5;

        return -1;
    }

    // ================= TTS =================

    private void speak(String text, String id) {
        isSpeaking = true;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (tts != null) tts.shutdown();
    }
}