package flash.embeddedserver.jetty.websocket;

import jakarta.websocket.Endpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

/**
 * A wrapper for WebSocket handler classes/instances that are compatible with Jakarta WebSocket API (Jakarta 5).
 */
public interface WebSocketHandlerWrapper {

    /**
     * Gets the actual handler - if necessary, instantiating an object.
     *
     * @return The handler instance.
     */
    Object getHandler();

    /**
     * Gets the ServerEndpointConfigurator for this WebSocket handler.
     *
     * @return the configurator or null if no custom configurator is needed.
     */
    default ServerEndpointConfig.Configurator getConfigurator() {
        return new ServerEndpointConfig.Configurator() {

        };
    }

    /**
     * Validates that the handler class is either a WebSocketListener or annotated with @WebSocket.
     *
     * @param handlerClass the handler class to validate
     * @throws IllegalArgumentException if the handler is invalid
     */
    static void validateHandlerClass(Class<?> handlerClass) {
        boolean valid = WebSocketListener.class.isAssignableFrom(handlerClass)
            || handlerClass.isAnnotationPresent(WebSocket.class)
            || Endpoint.class.isAssignableFrom(handlerClass);
        if (!valid) {
            throw new IllegalArgumentException(
                "WebSocket handler must implement 'WebSocketListener', be annotated as '@WebSocket', or be an instance of 'Endpoint'");
        }
    }
}
