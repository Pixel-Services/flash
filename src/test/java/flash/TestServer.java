package flash;

import flash.handlers.TestHandler;
import flash.interceptors.AuthInterceptor;

import java.util.List;

public class TestServer {
    public static void main(String[] args) {
        FlashServer server = new FlashServer("My first Flash server");
        server.port(8080);
        server.route("/test")
            .register(TestHandler.class, List.of(new AuthInterceptor()));

        server.start();
    }
}
