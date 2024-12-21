package flash;

import flash.handlers.TestHandler;
import flash.interceptors.AuthInterceptor;
import flash.swagger.FlashSwaggerConfiguration;

import java.util.List;

public class TestServer {
    public static void main(String[] args) {
        FlashServer server = new FlashServer("My first Flash server");
        server.port(8080);
        server.route("/test")
            .register(TestHandler.class, List.of(new AuthInterceptor()));

        server.swagger("/swagger", new FlashSwaggerConfiguration(
            "Test Server API Specification",
            "A simple server to test Flash",
            "1.0.0",
            List.of("http://localhost:8080")
        ));

        server.start();
    }
}
