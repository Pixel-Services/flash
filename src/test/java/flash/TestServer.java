package flash;

import flash.config.FlashConfiguration;
import flash.route.RouteController;

import static flash.Flash.*;

public class TestServer {
    public static void main(String[] args) {
        // Set the port for the server
        port(8080);

        // Register WebSocket handler
        webSocket("/ws", TestWebsocketHandler.class);

        // You can also register additional route controllers
        new RouteController("/api")
            .register(TestHandler.class);

        // Start the server
        init();

        // Load & Test Config
        FlashConfiguration config = new FlashConfiguration();
        config.set("test", "Hello from the config!");
        System.out.println(config.get("test"));
    }
}
