package com.pixelervices.flash.tests;

import com.pixelervices.flash.utils.RequestPerformer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReqParamHandlerTest {
    @Test
    public void testReqParamTestHandler() {
        String response = RequestPerformer.sendGetRequest("http://localhost:8080/test/reqparam?testParam=John");
        assertEquals("Test param: John", response);
    }
}
