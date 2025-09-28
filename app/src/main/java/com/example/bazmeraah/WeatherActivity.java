package com.example.bazmeraah; // <-- apne project ka package name daalo

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

public class WeatherActivity extends AppCompatActivity {

    private TextView tvWeatherResult;
    private Button btnFetchWeather;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather);

        tvWeatherResult = findViewById(R.id.tvWeatherResult);
        btnFetchWeather = findViewById(R.id.btnFetchWeather);

        btnFetchWeather.setOnClickListener(v -> {
            fetchWeather("Islamabad"); // test city
        });
    }

    private void fetchWeather(String city) {
        String apiKey = "41b483538c857e91a1ee70b2b90de6b0"; // 👈 yahan apna OpenWeatherMap API key lagao
        String url = "https://api.openweathermap.org/data/2.5/weather?q=" +
                city + "&appid=" + apiKey + "&units=metric";

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    try {
                        JSONObject main = response.getJSONObject("main");
                        double temp = main.getDouble("temp");
                        String weatherInfo = "City: " + city + "\nTemp: " + temp + "°C";
                        tvWeatherResult.setText(weatherInfo);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        tvWeatherResult.setText("Parsing error: " + e.getMessage());
                    }
                },
                error -> tvWeatherResult.setText("Request error: " + error.getMessage())
        );

        queue.add(request);
    }
}
