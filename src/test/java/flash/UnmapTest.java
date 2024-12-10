package flash;

import org.junit.Assert;
import org.junit.Test;

import flash.util.FlashTestUtil;

import static flash.Flash.awaitInitialization;
import static flash.Flash.get;
import static flash.Flash.unmap;

public class UnmapTest {

    FlashTestUtil testUtil = new FlashTestUtil(4567);

    @Test
    public void testUnmap() throws Exception {
        get("/tobeunmapped", (q, a) -> "tobeunmapped");
        awaitInitialization();

        FlashTestUtil.UrlResponse response = testUtil.doMethod("GET", "/tobeunmapped", null);
        Assert.assertEquals(200, response.status);
        Assert.assertEquals("tobeunmapped", response.body);

        unmap("/tobeunmapped");

        response = testUtil.doMethod("GET", "/tobeunmapped", null);
        Assert.assertEquals(404, response.status);

        get("/tobeunmapped", (q, a) -> "tobeunmapped");

        response = testUtil.doMethod("GET", "/tobeunmapped", null);
        Assert.assertEquals(200, response.status);
        Assert.assertEquals("tobeunmapped", response.body);

        unmap("/tobeunmapped", "get");

        response = testUtil.doMethod("GET", "/tobeunmapped", null);
        Assert.assertEquals(404, response.status);
    }
}
