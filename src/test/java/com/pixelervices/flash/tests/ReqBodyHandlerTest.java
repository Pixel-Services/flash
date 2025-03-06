package com.pixelervices.flash.tests;

import com.pixelervices.flash.utils.RequestPerformer;
import org.junit.Test;

import static com.pixelservices.flash.utils.PrettyLogger.logger;
import static org.junit.Assert.assertEquals;

public class ReqBodyHandlerTest {
    @Test
    public void testReqBodyTestHandler() {
        String response = RequestPerformer.performPostRequestBodyField("http://localhost:8080/test/reqbody", "testParam", "John");
        logger.info(response);
        assertEquals("Test body: John", response);
    }
}
