package flash;

import flash.config.FlashConfiguration;
import flash.handlers.TestHandler;
import flash.route.RouteController;

import static flash.FlashServerHelper.*;

public class TestServer {
    public static void main(String[] args) {
        FlashServer server = new FlashServer("My first Flash server");
        server.port(8080);
        server.route("/test")
            .register(TestHandler.class);

        server.start();
    }
}
