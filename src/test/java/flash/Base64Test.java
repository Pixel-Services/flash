package flash;

import org.junit.Assert;
import org.junit.Test;

public class Base64Test {

    @Test
    public final void test_encode() {
        String in = "hello";
        String encode = Base64.encode(in);
        Assert.assertFalse(in.equals(encode));
    }

    @Test
    public final void test_decode() {
        String in = "hello";
        String encode = Base64.encode(in);
        String decode = Base64.decode(encode);

        Assert.assertTrue(in.equals(decode));
    }

}
