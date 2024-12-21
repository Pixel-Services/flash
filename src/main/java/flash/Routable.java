/*
 * Copyright 2015 - Per Wendel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package flash;

import flash.route.HttpMethod;
import flash.utils.FlashUtils;

/**
 * Routable abstract class. Lets extending classes inherit default routable functionality.
 */
abstract class Routable {

    private ResponseTransformer defaultResponseTransformer;

    /**
     * Adds a route
     *
     * @param httpMethod the HTTP method
     * @param route      the route implementation
     */
    protected abstract void addRoute(HttpMethod httpMethod, RouteImpl route);

    @Deprecated
    protected abstract void addRoute(String httpMethod, RouteImpl route);

    /**
     * Adds a filter
     *
     * @param httpMethod the HTTP method
     * @param filter     the route implementation
     */
    protected abstract void addFilter(HttpMethod httpMethod, FilterImpl filter);

    @Deprecated
    protected abstract void addFilter(String httpMethod, FilterImpl filter);

    /////////////////////////////
    // Default implementations //

    /**
     * Map the route for HTTP GET requests
     *
     * @param path  the path
     * @param route The route
     */
    public void get(String path, Route route) {
        addRoute(HttpMethod.GET, createRouteImpl(path, route));
    }

    /**
     * Map the route for HTTP POST requests
     *
     * @param path  the path
     * @param route The route
     */
    public void post(String path, Route route) {
        addRoute(HttpMethod.POST, createRouteImpl(path, route));
    }

    /**
     * Map the route for HTTP PUT requests
     *
     * @param path  the path
     * @param route The route
     */
    public void put(String path, Route route) {
        addRoute(HttpMethod.PUT, createRouteImpl(path, route));
    }

    /**
     * Map the route for HTTP PATCH requests
     *
     * @param path  the path
     * @param route The route
     */
    public void patch(String path, Route route) {
        addRoute(HttpMethod.PATCH, createRouteImpl(path, route));
    }

    /**
     * Map the route for HTTP DELETE requests
     *
     * @param path  the path
     * @param route The route
     */
    public void delete(String path, Route route) {
        addRoute(HttpMethod.DELETE, createRouteImpl(path, route));
    }

    /**
     * Map the route for HTTP HEAD requests
     *
     * @param path  the path
     * @param route The route
     */
    public void head(String path, Route route) {
        addRoute(HttpMethod.HEAD, createRouteImpl(path, route));
    }

    /**
     * Map the route for HTTP TRACE requests
     *
     * @param path  the path
     * @param route The route
     */
    public void trace(String path, Route route) {
        addRoute(HttpMethod.TRACE, createRouteImpl(path, route));
    }

    /**
     * Map the route for HTTP CONNECT requests
     *
     * @param path  the path
     * @param route The route
     */
    public void connect(String path, Route route) {
        addRoute(HttpMethod.CONNECT, createRouteImpl(path, route));
    }

    /**
     * Map the route for HTTP OPTIONS requests
     *
     * @param path  the path
     * @param route The route
     */
    public void options(String path, Route route) {
        addRoute(HttpMethod.OPTIONS, createRouteImpl(path, route));
    }

    /**
     * Maps a filter to be executed BEFORE any matching routes
     *
     * @param path   the path
     * @param filter The filter
     */
    public void before(String path, Filter filter) {
        addFilter(HttpMethod.BEFORE, FilterImpl.create(path, filter));
    }

    /**
     * Maps a filter to be executed AFTER any matching routes
     *
     * @param path   the path
     * @param filter The filter
     */
    public void after(String path, Filter filter) {
        addFilter(HttpMethod.AFTER, FilterImpl.create(path, filter));
    }

    //////////////////////////////////////////////////
    // BEGIN route/filter mapping with accept type
    //////////////////////////////////////////////////

    /**
     * Map the route for HTTP GET requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     */
    public void get(String path, String acceptType, Route route) {
        addRoute(HttpMethod.GET, createRouteImpl(path, acceptType, route));
    }

    /**
     * Map the route for HTTP POST requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     */
    public void post(String path, String acceptType, Route route) {
        addRoute(HttpMethod.POST, createRouteImpl(path, acceptType, route));
    }

    /**
     * Map the route for HTTP PUT requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     */
    public void put(String path, String acceptType, Route route) {
        addRoute(HttpMethod.PUT, createRouteImpl(path, acceptType, route));
    }

    /**
     * Map the route for HTTP PATCH requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     */
    public void patch(String path, String acceptType, Route route) {
        addRoute(HttpMethod.PATCH, createRouteImpl(path, acceptType, route));
    }

    /**
     * Map the route for HTTP DELETE requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     */
    public void delete(String path, String acceptType, Route route) {
        addRoute(HttpMethod.DELETE, createRouteImpl(path, acceptType, route));
    }

    /**
     * Map the route for HTTP HEAD requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     */
    public void head(String path, String acceptType, Route route) {
        addRoute(HttpMethod.HEAD, createRouteImpl(path, acceptType, route));
    }

    /**
     * Map the route for HTTP TRACE requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     */
    public void trace(String path, String acceptType, Route route) {
        addRoute(HttpMethod.TRACE, createRouteImpl(path, acceptType, route));
    }

    /**
     * Map the route for HTTP CONNECT requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     */
    public void connect(String path, String acceptType, Route route) {
        addRoute(HttpMethod.CONNECT, createRouteImpl(path, acceptType, route));
    }

    /**
     * Map the route for HTTP OPTIONS requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     */
    public void options(String path, String acceptType, Route route) {
        addRoute(HttpMethod.OPTIONS, createRouteImpl(path, acceptType, route));
    }


    /**
     * Maps a filter to be executed BEFORE any matching routes
     *
     * @param filter The filter
     */
    public void before(Filter filter) {
        addFilter(HttpMethod.BEFORE, FilterImpl.create(FlashUtils.ALL_PATHS, filter));
    }

    /**
     * Maps a filter to be executed AFTER any matching routes
     *
     * @param filter The filter
     */
    public void after(Filter filter) {
        addFilter(HttpMethod.AFTER, FilterImpl.create(FlashUtils.ALL_PATHS, filter));
    }

    /**
     * Maps a filter to be executed BEFORE any matching routes
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param filter     The filter
     */
    public void before(String path, String acceptType, Filter filter) {
        addFilter(HttpMethod.BEFORE, FilterImpl.create(path, acceptType, filter));
    }

    /**
     * Maps a filter to be executed AFTER any matching routes
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param filter     The filter
     */
    public void after(String path, String acceptType, Filter filter) {
        addFilter(HttpMethod.AFTER, FilterImpl.create(path, acceptType, filter));
    }

    /**
     * Maps a filter to be executed AFTER any matching routes even if the route throws any exception
     *
     * @param filter The filter
     */
    public void afterAfter(Filter filter) {
        addFilter(HttpMethod.AFTERAFTER, FilterImpl.create(FlashUtils.ALL_PATHS, filter));
    }

    /**
     * Maps a filter to be executed AFTER any matching routes even if the route throws any exception
     *
     * @param filter The filter
     */
    public void afterAfter(String path, Filter filter) {
        addFilter(HttpMethod.AFTERAFTER, FilterImpl.create(path, filter));
    }

    //////////////////////////////////////////////////
    // END route/filter mapping with accept type
    //////////////////////////////////////////////////

    //////////////////////////////////////////////////
    // BEGIN Template View Routes
    //////////////////////////////////////////////////

    /**
     * Map the route for HTTP GET requests
     *
     * @param path   the path
     * @param route  The route
     * @param engine the template engine
     */
    public void get(String path, TemplateViewRoute route, TemplateEngine engine) {
        addRoute(HttpMethod.GET, TemplateViewRouteImpl.create(path, route, engine));
    }

    /**
     * Map the route for HTTP GET requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     * @param engine     the template engine
     */
    public void get(String path,
                    String acceptType,
                    TemplateViewRoute route,
                    TemplateEngine engine) {
        addRoute(HttpMethod.GET, TemplateViewRouteImpl.create(path, acceptType, route, engine));
    }

    /**
     * Map the route for HTTP POST requests
     *
     * @param path   the path
     * @param route  The route
     * @param engine the template engine
     */
    public void post(String path, TemplateViewRoute route, TemplateEngine engine) {
        addRoute(HttpMethod.POST, TemplateViewRouteImpl.create(path, route, engine));
    }

    /**
     * Map the route for HTTP POST requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     * @param engine     the template engine
     */
    public void post(String path,
                     String acceptType,
                     TemplateViewRoute route,
                     TemplateEngine engine) {
        addRoute(HttpMethod.POST, TemplateViewRouteImpl.create(path, acceptType, route, engine));
    }

    /**
     * Map the route for HTTP PUT requests
     *
     * @param path   the path
     * @param route  The route
     * @param engine the template engine
     */
    public void put(String path, TemplateViewRoute route, TemplateEngine engine) {
        addRoute(HttpMethod.PUT, TemplateViewRouteImpl.create(path, route, engine));
    }

    /**
     * Map the route for HTTP PUT requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     * @param engine     the template engine
     */
    public void put(String path,
                    String acceptType,
                    TemplateViewRoute route,
                    TemplateEngine engine) {
        addRoute(HttpMethod.PUT, TemplateViewRouteImpl.create(path, acceptType, route, engine));
    }

    /**
     * Map the route for HTTP DELETE requests
     *
     * @param path   the path
     * @param route  The route
     * @param engine the template engine
     */
    public void delete(String path, TemplateViewRoute route, TemplateEngine engine) {
        addRoute(HttpMethod.DELETE, TemplateViewRouteImpl.create(path, route, engine));
    }

    /**
     * Map the route for HTTP DELETE requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     * @param engine     the template engine
     */
    public void delete(String path,
                       String acceptType,
                       TemplateViewRoute route,
                       TemplateEngine engine) {
        addRoute(HttpMethod.DELETE, TemplateViewRouteImpl.create(path, acceptType, route, engine));
    }

    /**
     * Map the route for HTTP PATCH requests
     *
     * @param path   the path
     * @param route  The route
     * @param engine the template engine
     */
    public void patch(String path, TemplateViewRoute route, TemplateEngine engine) {
        addRoute(HttpMethod.PATCH, TemplateViewRouteImpl.create(path, route, engine));
    }

    /**
     * Map the route for HTTP PATCH requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     * @param engine     the template engine
     */
    public void patch(String path,
                      String acceptType,
                      TemplateViewRoute route,
                      TemplateEngine engine) {
        addRoute(HttpMethod.PATCH, TemplateViewRouteImpl.create(path, acceptType, route, engine));
    }

    /**
     * Map the route for HTTP HEAD requests
     *
     * @param path   the path
     * @param route  The route
     * @param engine the template engine
     */
    public void head(String path, TemplateViewRoute route, TemplateEngine engine) {
        addRoute(HttpMethod.HEAD, TemplateViewRouteImpl.create(path, route, engine));
    }

    /**
     * Map the route for HTTP HEAD requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     * @param engine     the template engine
     */
    public void head(String path,
                     String acceptType,
                     TemplateViewRoute route,
                     TemplateEngine engine) {
        addRoute(HttpMethod.HEAD, TemplateViewRouteImpl.create(path, acceptType, route, engine));
    }

    /**
     * Map the route for HTTP TRACE requests
     *
     * @param path   the path
     * @param route  The route
     * @param engine the template engine
     */
    public void trace(String path, TemplateViewRoute route, TemplateEngine engine) {
        addRoute(HttpMethod.TRACE, TemplateViewRouteImpl.create(path, route, engine));
    }

    /**
     * Map the route for HTTP TRACE requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     * @param engine     the template engine
     */
    public void trace(String path,
                      String acceptType,
                      TemplateViewRoute route,
                      TemplateEngine engine) {
        addRoute(HttpMethod.TRACE, TemplateViewRouteImpl.create(path, acceptType, route, engine));
    }

    /**
     * Map the route for HTTP CONNECT requests
     *
     * @param path   the path
     * @param route  The route
     * @param engine the template engine
     */
    public void connect(String path, TemplateViewRoute route, TemplateEngine engine) {
        addRoute(HttpMethod.CONNECT, TemplateViewRouteImpl.create(path, route, engine));
    }

    /**
     * Map the route for HTTP CONNECT requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     * @param engine     the template engine
     */
    public void connect(String path,
                        String acceptType,
                        TemplateViewRoute route,
                        TemplateEngine engine) {
        addRoute(HttpMethod.CONNECT, TemplateViewRouteImpl.create(path, acceptType, route, engine));
    }

    /**
     * Map the route for HTTP OPTIONS requests
     *
     * @param path   the path
     * @param route  The route
     * @param engine the template engine
     */
    public void options(String path, TemplateViewRoute route, TemplateEngine engine) {
        addRoute(HttpMethod.OPTIONS, TemplateViewRouteImpl.create(path, route, engine));
    }

    /**
     * Map the route for HTTP OPTIONS requests
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      The route
     * @param engine     the template engine
     */
    public void options(String path,
                        String acceptType,
                        TemplateViewRoute route,
                        TemplateEngine engine) {
        addRoute(HttpMethod.OPTIONS, TemplateViewRouteImpl.create(path, acceptType, route, engine));
    }

    //////////////////////////////////////////////////
    // END Template View Routes
    //////////////////////////////////////////////////

    //////////////////////////////////////////////////
    // BEGIN Response Transforming Routes
    //////////////////////////////////////////////////

    /**
     * Map the route for HTTP GET requests
     *
     * @param path        the path
     * @param route       The route
     * @param transformer the response transformer
     */
    public void get(String path, Route route, ResponseTransformer transformer) {
        addRoute(HttpMethod.GET, ResponseTransformerRouteImpl.create(path, route, transformer));
    }

    /**
     * Map the route for HTTP GET requests
     *
     * @param path        the path
     * @param acceptType  the accept type
     * @param route       The route
     * @param transformer the response transformer
     */
    public void get(String path, String acceptType, Route route, ResponseTransformer transformer) {
        addRoute(HttpMethod.GET, ResponseTransformerRouteImpl.create(path, acceptType, route, transformer));
    }

    /**
     * Map the route for HTTP POST requests
     *
     * @param path        the path
     * @param route       The route
     * @param transformer the response transformer
     */
    public void post(String path, Route route, ResponseTransformer transformer) {
        addRoute(HttpMethod.POST, ResponseTransformerRouteImpl.create(path, route, transformer));
    }

    /**
     * Map the route for HTTP POST requests
     *
     * @param path        the path
     * @param acceptType  the accept type
     * @param route       The route
     * @param transformer the response transformer
     */
    public void post(String path, String acceptType, Route route, ResponseTransformer transformer) {
        addRoute(HttpMethod.POST, ResponseTransformerRouteImpl.create(path, acceptType, route, transformer));
    }

    /**
     * Map the route for HTTP PUT requests
     *
     * @param path        the path
     * @param route       The route
     * @param transformer the response transformer
     */
    public void put(String path, Route route, ResponseTransformer transformer) {
        addRoute(HttpMethod.PUT, ResponseTransformerRouteImpl.create(path, route, transformer));
    }

    /**
     * Map the route for HTTP PUT requests
     *
     * @param path        the path
     * @param acceptType  the accept type
     * @param route       The route
     * @param transformer the response transformer
     */
    public void put(String path, String acceptType, Route route, ResponseTransformer transformer) {
        addRoute(HttpMethod.PUT, ResponseTransformerRouteImpl.create(path, acceptType, route, transformer));
    }

    /**
     * Map the route for HTTP DELETE requests
     *
     * @param path        the path
     * @param route       The route
     * @param transformer the response transformer
     */
    public void delete(String path, Route route, ResponseTransformer transformer) {
        addRoute(HttpMethod.DELETE, ResponseTransformerRouteImpl.create(path, route, transformer));
    }

    /**
     * Map the route for HTTP DELETE requests
     *
     * @param path        the path
     * @param acceptType  the accept type
     * @param route       The route
     * @param transformer the response transformer
     */
    public void delete(String path,
                       String acceptType,
                       Route route,
                       ResponseTransformer transformer) {
        addRoute(HttpMethod.DELETE, ResponseTransformerRouteImpl.create(path, acceptType, route, transformer));
    }

    /**
     * Map the route for HTTP HEAD requests
     *
     * @param path        the path
     * @param route       The route
     * @param transformer the response transformer
     */
    public void head(String path, Route route, ResponseTransformer transformer) {
        addRoute(HttpMethod.HEAD, ResponseTransformerRouteImpl.create(path, route, transformer));
    }

    /**
     * Map the route for HTTP HEAD requests
     *
     * @param path        the path
     * @param acceptType  the accept type
     * @param route       The route
     * @param transformer the response transformer
     */
    public void head(String path, String acceptType, Route route, ResponseTransformer transformer) {
        addRoute(HttpMethod.HEAD, ResponseTransformerRouteImpl.create(path, acceptType, route, transformer));
    }

    /**
     * Map the route for HTTP CONNECT requests
     *
     * @param path        the path
     * @param route       The route
     * @param transformer the response transformer
     */
    public void connect(String path, Route route, ResponseTransformer transformer) {
        addRoute(HttpMethod.CONNECT, ResponseTransformerRouteImpl.create(path, route, transformer));
    }

    /**
     * Map the route for HTTP CONNECT requests
     *
     * @param path        the path
     * @param acceptType  the accept type
     * @param route       The route
     * @param transformer the response transformer
     */
    public void connect(String path,
                        String acceptType,
                        Route route,
                        ResponseTransformer transformer) {
        addRoute(HttpMethod.CONNECT, ResponseTransformerRouteImpl.create(path, acceptType, route, transformer));
    }

    /**
     * Map the route for HTTP TRACE requests
     *
     * @param path        the path
     * @param route       The route
     * @param transformer the response transformer
     */
    public void trace(String path, Route route, ResponseTransformer transformer) {
        addRoute(HttpMethod.TRACE, ResponseTransformerRouteImpl.create(path, route, transformer));
    }

    /**
     * Map the route for HTTP TRACE requests
     *
     * @param path        the path
     * @param acceptType  the accept type
     * @param route       The route
     * @param transformer the response transformer
     */
    public void trace(String path,
                      String acceptType,
                      Route route,
                      ResponseTransformer transformer) {
        addRoute(HttpMethod.TRACE, ResponseTransformerRouteImpl.create(path, acceptType, route, transformer));
    }

    /**
     * Map the route for HTTP OPTIONS requests
     *
     * @param path        the path
     * @param route       The route
     * @param transformer the response transformer
     */
    public void options(String path, Route route, ResponseTransformer transformer) {
        addRoute(HttpMethod.OPTIONS, ResponseTransformerRouteImpl.create(path, route, transformer));
    }

    /**
     * Map the route for HTTP OPTIONS requests
     *
     * @param path        the path
     * @param acceptType  the accept type
     * @param route       The route
     * @param transformer the response transformer
     */
    public void options(String path,
                        String acceptType,
                        Route route,
                        ResponseTransformer transformer) {
        addRoute(HttpMethod.OPTIONS, ResponseTransformerRouteImpl.create(path, acceptType, route, transformer));
    }

    /**
     * Map the route for HTTP PATCH requests
     *
     * @param path        the path
     * @param route       The route
     * @param transformer the response transformer
     */
    public void patch(String path, Route route, ResponseTransformer transformer) {
        addRoute(HttpMethod.PATCH, ResponseTransformerRouteImpl.create(path, route, transformer));
    }

    /**
     * Map the route for HTTP PATCH requests
     *
     * @param path        the path
     * @param acceptType  the accept type
     * @param route       The route
     * @param transformer the response transformer
     */
    public void patch(String path,
                      String acceptType,
                      Route route,
                      ResponseTransformer transformer) {
        addRoute(HttpMethod.PATCH, ResponseTransformerRouteImpl.create(path, acceptType, route, transformer));
    }

    /**
     * Create route implementation or use default response transformer
     *
     * @param path       the path
     * @param acceptType the accept type
     * @param route      the route
     * @return ResponseTransformerRouteImpl or RouteImpl
     */
    private RouteImpl createRouteImpl(String path, String acceptType, Route route) {
        if (defaultResponseTransformer != null) {
            return ResponseTransformerRouteImpl.create(path, acceptType, route, defaultResponseTransformer);
        }
        return RouteImpl.create(path, acceptType, route);
    }

    /**
     * Create route implementation or use default response transformer
     *
     * @param path  the path
     * @param route the route
     * @return ResponseTransformerRouteImpl or RouteImpl
     */
    private RouteImpl createRouteImpl(String path, Route route) {
        if (defaultResponseTransformer != null) {
            return ResponseTransformerRouteImpl.create(path, route, defaultResponseTransformer);
        }
        return RouteImpl.create(path, route);
    }

    /**
     * Sets default response transformer
     *
     * @param transformer
     */
    public void defaultResponseTransformer(ResponseTransformer transformer) {
        defaultResponseTransformer = transformer;
    }
}
