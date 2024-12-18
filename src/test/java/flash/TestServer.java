package flash;

import flash.handlers.TestHandler;

public class TestServer {
    public static void main(String[] args) {
        FlashServer server = new FlashServer("My first Flash server");
        server.port(8080);
        server.route("/test")
            .register(TestHandler.class);

        server.start();
    }
}
