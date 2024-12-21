package flash.embeddedserver.jetty.websocket;

import java.util.Map;
import java.util.Optional;

import jakarta.websocket.server.ServerEndpointConfig;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;

/**
 * Creates websocket servlet context handlers.
 */
public class WebSocketServletContextHandlerFactory {

    public static ServletContextHandler create(Map<String, WebSocketHandlerWrapper> webSocketHandlers,
                                               Optional<Long> webSocketIdleTimeoutMillis) {
        ServletContextHandler webSocketServletContextHandler = null;

        if (webSocketHandlers != null) {
            try {
                // Create a new ServletContextHandler
                webSocketServletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
                webSocketServletContextHandler.setContextPath("/ws");

                // Configure Jakarta WebSocket initializer
                JakartaWebSocketServletContainerInitializer.configure(
                    webSocketServletContextHandler,
                    (servletContext, wsContainer) -> {
                        // Set default max session idle timeout if provided
                        webSocketIdleTimeoutMillis.ifPresent(wsContainer::setDefaultMaxSessionIdleTimeout);

                        // Map WebSocket handlers to paths
                        for (Map.Entry<String, WebSocketHandlerWrapper> entry : webSocketHandlers.entrySet()) {
                            String path = entry.getKey();
                            WebSocketHandlerWrapper handlerWrapper = entry.getValue();

                            // Create a ServerEndpointConfig from WebSocket handler
                            ServerEndpointConfig.Builder configBuilder = ServerEndpointConfig
                                .Builder.create(handlerWrapper.getHandler().getClass(), path);

                            ServerEndpointConfig endpointConfig = configBuilder.build();

                            // Add WebSocket endpoint with the config
                            wsContainer.addEndpoint(endpointConfig);
                        }
                    }
                );
            } catch (Exception ex) {
                System.out.println("Creation of WebSocket context handler failed: " + ex);
                webSocketServletContextHandler = null;
            }
        }

        return webSocketServletContextHandler;
    }
}
