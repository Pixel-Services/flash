/*
 * Copyright 2011- Per Wendel
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
package flash.servlet;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import flash.ExceptionMapper;
import flash.globalstate.ServletFlag;
import flash.http.matching.MatcherFilter;
import flash.route.ServletRoutes;
import flash.staticfiles.StaticFilesConfiguration;
import flash.utils.StringUtils;

/**
 * Filter that can be configured to be used in a web.xml file.
 * Needs the init parameter 'applicationClass' set to the application class where
 * the adding of routes should be made.
 *
 * @author Per Wendel
 */
public class FlashFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(FlashFilter.class);

    public static final String APPLICATION_CLASS_PARAM = "applicationClass";

    private String filterPath;

    private MatcherFilter matcherFilter;

    /**
     * It contains all the Flash application instances that was declared in the filter configuration. They can be one or more
     * class names separated by commas.
     */
    private FlashApplication[] applications;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        ServletFlag.runFromServlet();

        applications = getApplications(filterConfig);

        for (FlashApplication application : applications) {
            application.init();
        }

        filterPath = FilterTools.getFilterPath(filterConfig);

        matcherFilter = new MatcherFilter(ServletRoutes.get(), StaticFilesConfiguration.servletInstance, ExceptionMapper.getServletInstance(), true, false);
    }

    /**
     * Returns an instance of {@link FlashApplication} which on which {@link FlashApplication#init() init()} will be called.
     * Default implementation looks up the class name in the filterConfig using the key {@value #APPLICATION_CLASS_PARAM}.
     * Subclasses can override this method to use different techniques to obtain an instance (i.e. dependency injection).
     *
     * @param filterConfig the filter configuration for retrieving parameters passed to this filter.
     * @return the flash application containing the configuration.
     * @throws ServletException if anything went wrong.
     * @deprecated Use {@link #getApplications(FilterConfig)} instead.
     */
    @Deprecated
    protected FlashApplication getApplication(FilterConfig filterConfig) throws ServletException {
        return getApplication(filterConfig.getInitParameter(APPLICATION_CLASS_PARAM));
    }

    /**
     * Returns an instance of {@link FlashApplication} which on which {@link FlashApplication#init() init()} will be called.
     * Default implementation looks up the class name in the filterConfig using the key {@value #APPLICATION_CLASS_PARAM}.
     * Subclasses can override this method to use different techniques to obtain an instance (i.e. dependency injection).
     *
     * @param applicationClassName the flash application class name passed to this filter.
     * @return the flash application containing the configuration.
     * @throws ServletException if anything went wrong.
     */
    protected FlashApplication getApplication(String applicationClassName) throws ServletException {
        try {
            Class<?> applicationClass = Class.forName(applicationClassName);
            return (FlashApplication) applicationClass.newInstance();
        } catch (Exception exc) {
            throw new ServletException(exc);
        }
    }

    /**
     * Returns the instances of {@link FlashApplication} which on which {@link FlashApplication#init() init()} will be called.
     * Default implementation looks up the class names in the filterConfig using the key {@value #APPLICATION_CLASS_PARAM}.
     * Subclasses can override this method to use different techniques to obtain an instance (i.e. dependency injection).
     *
     * @param filterConfig the filter configuration for retrieving parameters passed to this filter.
     * @return the flash applications containing the configuration.
     * @throws ServletException if anything went wrong.
     */
    protected FlashApplication[] getApplications(final FilterConfig filterConfig) throws ServletException {

        String applications = filterConfig.getInitParameter(APPLICATION_CLASS_PARAM);
        FlashApplication[] solvedApplications = null;

        if (StringUtils.isNotBlank(applications)) {
            final String[] flashApplications = applications.split(",");

            if (flashApplications != null && flashApplications.length > 0) {
                solvedApplications = new FlashApplication[flashApplications.length];

                for (int index = 0; index < flashApplications.length; index++) {
                    solvedApplications[index] = getApplication(flashApplications[index].trim());
                }
            } else {
                throw new ServletException("There are no Flash applications configured in the filter.");
            }
        }

        return solvedApplications;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws
                                                                                              IOException,
                                                                                              ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request; // NOSONAR
        HttpServletResponse httpResponse = (HttpServletResponse) response; // NOSONAR

        final String relativePath = FilterTools.getRelativePath(httpRequest, filterPath);

        if (LOG.isDebugEnabled()) {
            LOG.debug(relativePath);
        }

        HttpServletRequestWrapper requestWrapper = new HttpServletRequestWrapper(httpRequest) {
            @Override
            public String getPathInfo() {
                return relativePath;
            }

            @Override
            public String getRequestURI() {
                return relativePath;
            }
        };

        // handle static resources
        boolean consumed = StaticFilesConfiguration.servletInstance.consume(httpRequest, httpResponse);

        if (consumed) {
            return;
        }

        matcherFilter.doFilter(requestWrapper, response, chain);
    }

    @Override
    public void destroy() {
        if (applications != null) {
            for (FlashApplication flashApplication : applications) {
                flashApplication.destroy();
            }
        }
    }

}
