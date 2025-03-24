package com.pixelervices.flash;

import com.pixelervices.flash.tests.DefaultHandlersTest;
import com.pixelervices.flash.tests.FileHandlerTest;
import com.pixelervices.flash.tests.ReqBodyHandlerTest;
import com.pixelervices.flash.tests.ReqParamHandlerTest;
import com.pixelervices.flash.tests.WebsocketTest;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

public class TestServer {
    public static void main(String[] args) {
        Class<?>[] testClasses = {
                DefaultHandlersTest.class,
                FileHandlerTest.class,
                ReqBodyHandlerTest.class,
                ReqParamHandlerTest.class,
                WebsocketTest.class,
        };

        for (Class<?> testClass : testClasses) {
            Result result = JUnitCore.runClasses(testClass);
            for (Failure failure : result.getFailures()) {
                System.out.println(failure.toString());
            }
        }
    }
}
