package flash.examples.transformer;

import flash.ResponseTransformer;

import static flash.Flash.get;
import static flash.Flash.defaultResponseTransformer;

public class DefaultTransformerExample {

    public static void main(String args[]) {

        defaultResponseTransformer(json);

        get("/hello", "application/json", (request, response) -> {
            return new MyMessage("Hello World");
        });

        get("/hello2", "application/json", (request, response) -> {
            return new MyMessage("Hello World");
        }, model -> "custom transformer");
    }

    private static final ResponseTransformer json = new JsonTransformer();

}
