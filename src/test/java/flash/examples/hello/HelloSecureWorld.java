package flash.examples.hello;

import static flash.Flash.get;
import static flash.Flash.secure;

/**
 * You'll need to provide a JKS keystore as arg 0 and its password as arg 1.
 */
public class HelloSecureWorld {
    public static void main(String[] args) {

        secure(args[0], args[1], null, null);
        get("/hello", (request, response) -> {
            return "Hello Secure World!";
        });

    }
}
