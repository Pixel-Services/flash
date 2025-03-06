package com.pixelervices.flash.tests;

import com.pixelervices.flash.BaseTest;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import static org.junit.Assert.*;

public class DefaultHandlersTest extends BaseTest {

    @Test
    public void testDefaultHandlers() {
        testEndpoint("/param/John", "Hello, John");
        testEndpoint("/helloworld", "Hello, World!");
    }

    private void testEndpoint(String endpoint, String expectedResponse) {
        String response = sendGetRequest("http://localhost:8080/test" + endpoint);
        assertEquals(expectedResponse, response);
    }

    private String sendGetRequest(String urlString) {
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
}