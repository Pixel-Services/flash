/*
 * Copyright 2011- Per Wendel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package flash.embeddedserver.jetty.websocket;

import org.eclipse.jetty.websocket.core.server.WebSocketCreator;
import org.eclipse.jetty.websocket.core.server.ServerUpgradeRequest;
import org.eclipse.jetty.websocket.core.server.ServerUpgradeResponse;

import static java.util.Objects.requireNonNull;

/**
 * Factory class to create {@link WebSocketCreator} implementations that
 * delegate to the given handler class.
 *
 * @author Ignasi Barrera
 */
public class WebSocketCreatorFactory {

    /**
     * Creates a {@link WebSocketCreator} that uses the given handler class/instance for
     * the WebSocket connections.
     *
     * @param handlerWrapper The wrapped handler to use to manage WebSocket connections.
     * @return The WebSocketCreator.
     */
    public static WebSocketCreator create(WebSocketHandlerWrapper handlerWrapper) {
        return new FlashWebSocketCreator(handlerWrapper.getHandler());
    }

    // Package protected to be visible to the unit tests
    static class FlashWebSocketCreator implements WebSocketCreator {
        private final Object handler;

        private FlashWebSocketCreator(Object handler) {
            this.handler = requireNonNull(handler, "handler cannot be null");
        }

        @Override
        public Object createWebSocket(ServerUpgradeRequest request,
                                      ServerUpgradeResponse response) {
            return handler;
        }

        Object getHandler() {
            return handler;
        }
    }
}
