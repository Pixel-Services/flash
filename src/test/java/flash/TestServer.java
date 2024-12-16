package flash;

import flash.config.FlashConfiguration;
import flash.handlers.TestHandler;
import flash.models.RequestHandler;
import flash.route.RouteController;
import flash.swagger.SwaggerGenerator;

import java.util.List;

import static flash.FlashServerHelper.*;

public class TestServer {
    public static void main(String[] args) {
        // Set the port for the server
        port(8080);

        // Register WebSocket handler
        webSocket("/ws", TestWebsocketHandler.class);

        // You can also register additional route controllers
        RouteController apiController = new RouteController("/api");

        apiController.register(TestHandler.class);
        SwaggerGenerator.saveSpec(System.getProperty("user.dir") + "/temp/swagger.json");


        // Start the server
        init();

        // Load & Test Config
        FlashConfiguration config = new FlashConfiguration();
        config.set("test", "Hello from the config!");
        System.out.println(config.get("test"));
    }
}
