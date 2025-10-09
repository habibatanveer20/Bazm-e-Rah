package com.example.bazmeraah;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
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
    private Button backButton;

    private static final String API_KEY = "41b483538c857e91a1ee70b2b90de6b0";

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Handler handler = new Handler();
    private boolean isAskingForCity = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather); // ✅ this must come before findViewById()

        // 🔹 Link XML views
        cityNameText = findViewById(R.id.cityNameText);
        temperatureText = findViewById(R.id.temperatureText);
        humidityText = findViewById(R.id.humidityText);
        windText = findViewById(R.id.windText);
        descriptionText = findViewById(R.id.descriptionText);
        weatherIcon = findViewById(R.id.weatherIcon);
        backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> {
            speak("Going back to main page.");
            startActivity(new Intent(WeatherActivity.this, MainActivity.class));
            finish();
        });

        // 🔹 Initialize Text-To-Speech
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                speak("You are on the weather page. Which city weather do you want to know?");
            }
        });

        // 🔹 Initialize Speech Recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        // 🔹 TTS listener for flow
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {}

            @Override
            public void onDone(String utteranceId) {
                handler.postDelayed(() -> {
                    if (isAskingForCity) {
                        startVoiceInput();
                    } else {
                        // after weather info spoken, ask next question
                        isAskingForCity = true;
                        speak("What would you like to do next? You can say another city or say back to go home.");
                    }
                }, 1000);
            }

            @Override
            public void onError(String utteranceId) {}
        });
    }

    // 🔹 Speak helper
    private void speak(String text) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts1");
    }

    // 🔹 Start voice input
    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}

            @Override
            public void onError(int error) {
                speak("Sorry, I didn't catch that. Please say again.");
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String command = matches.get(0).toLowerCase();

                    if (command.contains("back")) {
                        speak("Going back to main page.");
                        startActivity(new Intent(WeatherActivity.this, MainActivity.class));
                        finish();
                    } else {
                        String city = command.replace("weather", "").trim();
                        if (!city.isEmpty()) {
                            FetchWeatherData(city);
                        } else {
                            speak("Please say a valid city name.");
                        }
                    }
                }
            }
        });

        speechRecognizer.startListening(intent);
    }

    // 🔹 Fetch weather data
    private void FetchWeatherData(String cityName) {
        String url = "https://api.openweathermap.org/data/2.5/weather?q=" + cityName +
                "&appid=" + API_KEY + "&units=metric";

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
                runOnUiThread(() -> speak("Network error. Please try again."));
            }
        });
    }

    // 🔹 Update UI and speak result
    private void updateUI(String result) {
        if (result != null) {
            try {
                JSONObject jsonObject = new JSONObject(result);
                if (jsonObject.has("cod") && jsonObject.getInt("cod") != 200) {
                    speak("Sorry, I couldn't find that city. Please say again.");
                    return;
                }

                JSONObject main = jsonObject.getJSONObject("main");
                double temperature = main.getDouble("temp");
                double humidity = main.getDouble("humidity");
                double windSpeed = jsonObject.getJSONObject("wind").getDouble("speed");
                String description = jsonObject.getJSONArray("weather").getJSONObject(0).getString("description");

                cityNameText.setText(jsonObject.getString("name"));
                temperatureText.setText(String.format("%.0f°C", temperature));
                humidityText.setText("Humidity: " + (int) humidity + "%");
                windText.setText("Wind: " + (int) windSpeed + " km/h");
                descriptionText.setText(description);

                isAskingForCity = false;
                speak("Weather in " + jsonObject.getString("name") +
                        " is " + description +
                        ". Temperature " + (int) temperature + " degree Celsius. " +
                        "Humidity " + (int) humidity + " percent. " +
                        "Wind speed " + (int) windSpeed + " kilometers per hour.");

            } catch (JSONException e) {
                e.printStackTrace();
                speak("There was an error reading the weather data.");
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
