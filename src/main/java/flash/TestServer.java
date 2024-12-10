package flash;

import flash.route.RouteController;

import static flash.Flash.get;
import static flash.Flash.port;

public class TestServer {
    public static void main(String[] args) {
        port(8080);
        registerControllers();
    }

    private static void registerControllers() {
        get("/hello", (req, res) -> "hi there");
        new RouteController("/test")
            .register(TestHandler.class);
    }
}
