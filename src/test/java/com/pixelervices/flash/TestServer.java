package com.pixelervices.flash;

import com.pixelervices.flash.tests.*;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import static com.pixelservices.flash.utils.PrettyLogger.logger;

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
            logger.info(testClass.getSimpleName() + (result.wasSuccessful() ? " &#80EF80passed" : " &#FF746Cfailed"));
        }
    }
}
