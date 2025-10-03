package com.example.bazmeraah;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WeatherActivity extends AppCompatActivity {

    private TextView cityNameText, temperatureText, humidityText, descriptionText, windText;
    private ImageView weatherIcon;
    private EditText cityNameInput;

    private static final String API_KEY = "41b483538c857e91a1ee70b2b90de6b0";

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather);

        cityNameText = findViewById(R.id.cityNameText);
        temperatureText = findViewById(R.id.temperatureText);
        humidityText = findViewById(R.id.humidityText);
        windText = findViewById(R.id.windText);
        descriptionText = findViewById(R.id.descriptionText);
        weatherIcon = findViewById(R.id.weatherIcon);
        cityNameInput = findViewById(R.id.cityNameInput);

        // Initialize TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                speak("You are on weather page. Which city weather you want to know?");
            }
        });

        // Initialize Speech Recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        // Jaise hi TTS bolna complete kare, mic trigger kare
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {}

            @Override
            public void onDone(String utteranceId) {
                runOnUiThread(() -> startVoiceInput());
            }

            @Override
            public void onError(String utteranceId) {}
        });
    }

    private void speak(String text) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts1");
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        // Recognition Listener set
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {}

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                speak("Sorry, I did not catch that. Please say again.");
                startVoiceInput(); // dobara mic restart
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String userCommand = matches.get(0).toLowerCase();

                    if (userCommand.contains("back")) {
                        // ✅ Back command
                        cityNameInput.setText("Back");
                        speak("Going back to main page.");
                        Intent intent = new Intent(WeatherActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        // ✅ City name
                        String cityName = userCommand;
                        cityNameText.setText(cityName);
                        cityNameInput.setText(cityName);
                        FetchWeatherData(cityName);
                    }
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(intent);
    }

    private void FetchWeatherData(String cityName) {
        String url = "https://api.openweathermap.org/data/2.5/weather?q=" + cityName + "&appid=" + API_KEY + "&units=metric";

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();
            try {
                Response response = client.newCall(request).execute();
                String result = response.body().string();
                runOnUiThread(() -> updateUI(result));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void updateUI(String result) {
        if (result != null) {
            try {
                JSONObject jsonObject = new JSONObject(result);

                // ❌ Agar city not found ho
                if (jsonObject.has("cod") && jsonObject.getInt("cod") != 200) {
                    String message = jsonObject.optString("message", "City not found");
                    cityNameText.setText("Error");
                    descriptionText.setText(message);
                    speak("Sorry, I couldn’t find weather for that city. Please say again.");
                    startVoiceInput();
                    return;
                }

                JSONObject main = jsonObject.getJSONObject("main");
                double temperature = main.getDouble("temp");
                double humidity = main.getDouble("humidity");
                double windSpeed = jsonObject.getJSONObject("wind").getDouble("speed");

                String description = jsonObject.getJSONArray("weather").getJSONObject(0).getString("description");

                // ✅ Set UI
                cityNameText.setText(jsonObject.getString("name"));
                temperatureText.setText(String.format("%.0f°", temperature));
                humidityText.setText(String.format("%.0f%%", humidity));
                windText.setText(String.format("%.0f km/h", windSpeed));
                descriptionText.setText(description);

                // ✅ Speak weather details
                speak("Weather in " + jsonObject.getString("name") +
                        " is " + description +
                        ". Temperature " + (int) temperature + " degree Celsius. " +
                        "Humidity " + (int) humidity + " percent. " +
                        "Wind speed " + (int) windSpeed + " kilometers per hour.");

                // ✅ Mic ko dobara sun’ne ke liye start karo
                startVoiceInput();

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }
}
