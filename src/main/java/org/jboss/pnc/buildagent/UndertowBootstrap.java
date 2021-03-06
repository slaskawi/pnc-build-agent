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

package org.jboss.pnc.buildagent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.termd.core.pty.PtyMaster;
import io.termd.core.pty.PtyStatusEvent;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.jboss.pnc.buildagent.servlet.Download;
import org.jboss.pnc.buildagent.servlet.Upload;
import org.jboss.pnc.buildagent.servlet.Welcome;
import org.jboss.pnc.buildagent.spi.TaskStatusUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.undertow.servlet.Servlets.defaultContainer;
import static io.undertow.servlet.Servlets.deployment;
import static io.undertow.servlet.Servlets.servlet;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class UndertowBootstrap {

    Logger log = LoggerFactory.getLogger(UndertowBootstrap.class);

    final String host;
    final int port;
    final Main termdHandler;
    private final Executor executor = Executors.newFixedThreadPool(1);
    private final Collection<PtyMaster> runningTasks;

    public UndertowBootstrap(String host, int port, Main termdHandler, Collection runningTasks) {
        this.host = host;
        this.port = port;
        this.termdHandler = termdHandler;
        this.runningTasks = runningTasks;
    }

    public void bootstrap(final Consumer<Boolean> completionHandler) {

        String servletPath = "/";
        String socketPath = "/socket";

        DeploymentInfo servletBuilder = deployment()
                .setClassLoader(UndertowBootstrap.class.getClassLoader())
                .setContextPath(servletPath)
                .setDeploymentName("ROOT.war")
                .addServlets(
                        servlet("WelcomeServlet", Welcome.class)
                                .addMapping("/")
                                .addMapping("/index"),
                        servlet("UploaderServlet", Upload.class)
                                .addMapping("/upload/*"),
                        servlet("DownloaderServlet", Download.class)
                                .addMapping("/download/*"));

        DeploymentManager manager = defaultContainer().addDeployment(servletBuilder);
        manager.deploy();

        HttpHandler servletHandler = null;
        try {
            servletHandler = manager.start();
        } catch (ServletException e) {
            e.printStackTrace();//TODO handle exception
        }

        PathHandler pathHandler = Handlers.path(Handlers.redirect(servletPath))
                .addPrefixPath(servletPath, servletHandler)
                .addPrefixPath(socketPath, exchange -> UndertowBootstrap.this.handleRequest(exchange));

        Undertow undertow = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(pathHandler)
                .build();

        undertow.start();

        completionHandler.accept(true);
    }

    private void handleRequest(HttpServerExchange exchange) throws Exception {
        String requestPath = exchange.getRequestPath();
        Sender responseSender = exchange.getResponseSender();

        if (requestPath.equals("/socket/term")) {
            getWebSocketHandler().handleRequest(exchange);
            return;
        }
        if (requestPath.equals("/socket/process-status-updates")) {
            webSocketStatusUpdateHandler().handleRequest(exchange);
            return;
        }
        if (requestPath.equals("/processes")) {
            getProcessStatusHandler().handleRequest(exchange);
            return;
        }
    }

    private HttpHandler getProcessStatusHandler() {
        return exchange -> {
            Map<String, Object> tasksMap = runningTasks.stream().collect(Collectors.toMap(t -> String.valueOf(t.getId()), t -> t.getStatus().toString()));
            ObjectMapper mapper = new ObjectMapper();
            String jsonString = mapper.writeValueAsString(tasksMap);
            exchange.getResponseSender().send(jsonString);
        };
    }

    private HttpHandler getWebSocketHandler() {
        WebSocketConnectionCallback webSocketConnectionCallback = new WebSocketConnectionCallback() {
            @Override
            public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel webSocketChannel) {
                WebSocketTtyConnection conn = new WebSocketTtyConnection(webSocketChannel, executor);
                termdHandler.getPtyBootstrap().accept(conn);
            }
        };

        HttpHandler webSocketHandshakeHandler = new WebSocketProtocolHandshakeHandler(webSocketConnectionCallback);
        return webSocketHandshakeHandler;
    }

    private HttpHandler webSocketStatusUpdateHandler() {
        WebSocketConnectionCallback webSocketConnectionCallback = new WebSocketConnectionCallback() {
            @Override
            public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel webSocketChannel) {
                Consumer<PtyStatusEvent> statusUpdateListener = (statusUpdateEvent) -> {
                    Map<String, Object> statusUpdate = new HashMap<>();
                    statusUpdate.put("action", "status-update");
                    TaskStatusUpdateEvent taskStatusUpdateEventWrapper = new TaskStatusUpdateEvent(statusUpdateEvent);
                    statusUpdate.put("event", taskStatusUpdateEventWrapper);

                    ObjectMapper objectMapper = new ObjectMapper();
                    try {
                        WebSockets.sendText(objectMapper.writeValueAsString(statusUpdate), webSocketChannel, null);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();//TODO
                    }
                };
                log.debug("Registering new status update listener {}.", statusUpdateListener);
                termdHandler.addStatusUpdateListener(statusUpdateListener);
                webSocketChannel.addCloseTask((task) -> termdHandler.removeStatusUpdateListener(statusUpdateListener));
            }
        };

        HttpHandler webSocketHandshakeHandler = new WebSocketProtocolHandshakeHandler(webSocketConnectionCallback);
        return webSocketHandshakeHandler;
    }
}
