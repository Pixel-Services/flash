package com.pixelervices.flash.utils;

import okhttp3.*;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class RequestPerformer {

    public static String sendGetRequest(String urlString) {
        String response = null;
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            Scanner scanner = new Scanner(conn.getInputStream());
            response = scanner.useDelimiter("\\A").next();
            scanner.close();
        } catch (Exception ignore) {}
        return response;
    }

    public static String performPostRequestBodyField(String serverUrl, String fieldName, String fieldValue) {
        OkHttpClient client = new OkHttpClient();

        JSONObject body = new JSONObject();
        body.put(fieldName, fieldValue);

        RequestBody requestBody = RequestBody.create(body.toString(), MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    public static String uploadFile(String serverUrl, File file) throws IOException {
        OkHttpClient client = new OkHttpClient();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(file, MediaType.parse("text/plain")))
                .build();

        Request request = new Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }
}
