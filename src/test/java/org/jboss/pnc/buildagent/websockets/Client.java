/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.pnc.buildagent.websockets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * @see "https://github.com/undertow-io/undertow/blob/5bdddf327209a4abf18792e78148863686c26e9b/websockets-jsr/src/test/java/io/undertow/websockets/jsr/test/BinaryEndpointTest.java"
 *
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Client {

    private static Logger log = LoggerFactory.getLogger(Client.class);

    ProgramaticClientEndpoint endpoint = new ProgramaticClientEndpoint();
    private Consumer<Session> onOpenConsumer;
    private Consumer<String> onStringMessageConsumer;
    private Consumer<byte[]> onBinaryMessageConsumer;
    private Consumer<CloseReason> onCloseConsumer;
    private Consumer<Throwable> onErrorConsumer;

    public Endpoint connect(String websocketUrl) throws Exception {
        ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().build();
        ContainerProvider.getWebSocketContainer().connectToServer(endpoint, clientEndpointConfig, new URI(websocketUrl));
        return endpoint;
    }

    public void close() throws Exception {
        log.debug("Client is closing connection.");
        endpoint.session.close();
//        endpoint.closeLatch.await(10, TimeUnit.SECONDS);
    }

    public void onOpen(Consumer<Session> onOpen) {
        onOpenConsumer = onOpen;
    }

    public void onStringMessage(Consumer<String> onStringMessage) {
        onStringMessageConsumer = onStringMessage;
    }

    public void onBinaryMessage(Consumer<byte[]> onBinaryMessage) {
        onBinaryMessageConsumer = onBinaryMessage;
    }

    public void onClose(Consumer<CloseReason> onClose) {
        onCloseConsumer = onClose;
    }

    public void onError(Consumer<Throwable> onError) {
        onErrorConsumer = onError;
    }

    public RemoteEndpoint.Basic getRemoteEndpoint() {
        return endpoint.session.getBasicRemote();
    }

    public class ProgramaticClientEndpoint extends Endpoint {
        final CountDownLatch closeLatch = new CountDownLatch(1); //TODO do we need latch ?
        volatile Session session;

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            log.debug("Client received open.");
            this.session = session;

            session.addMessageHandler(new MessageHandler.Whole<String>() {
                @Override
                public void onMessage(String message) {
                    log.trace("Client received text MESSAGE: {}", message);
                    if (onStringMessageConsumer != null) {
                        onStringMessageConsumer.accept(message);
                    }
                }
            });
            session.addMessageHandler(new MessageHandler.Whole<byte[]>() {
                @Override
                public void onMessage(byte[] bytes) {
                    log.trace("Client received binary MESSAGE: {}", new String(bytes));
                    if (onBinaryMessageConsumer != null) {
                        onBinaryMessageConsumer.accept(bytes);
                    }
                }
            });
            if (onOpenConsumer != null) {
                onOpenConsumer.accept(session);
            }
        }

        @Override
        public void onClose(Session session, CloseReason closeReason) {
            log.debug("Client received close.");
            onCloseConsumer.accept(closeReason);
//            closeLatch.countDown();
        }

        @Override
        public void onError(Session session, Throwable thr) {
            if (onErrorConsumer != null) {
                onErrorConsumer.accept(thr);
            } else {
                log.error("No error handler defined. Received error was: ", thr);
            }
        }
    }

}
