package flash.models;

import flash.Request;
import flash.Response;

public interface RequestHandlerInterceptor {
    boolean preHandle(Request request, Response response);
}
