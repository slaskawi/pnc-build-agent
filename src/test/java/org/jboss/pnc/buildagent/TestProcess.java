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

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class TestProcess {

    public static final String WELCOME_MESSAGE = "Hi there! I'm a long running process.";
    public static final String MESSAGE = "Hello again!";

    public static void main(String[] args) throws InterruptedException {

        int delay = 250;
        int repeat = 40;

        if (args.length >= 1) {
            repeat = Integer.parseInt(args[0]);
        }

        if (args.length >= 2) {
            delay = Integer.parseInt(args[1]);
        }

        System.out.println(WELCOME_MESSAGE);
        System.out.println("I'll write to stdout test message '" + MESSAGE + "' " + repeat + " times with " + delay + "ms delay.");
        for (int i = 0; i < repeat; i++) {
            System.out.println(i + " : " + MESSAGE);
            Thread.sleep(delay);
        }
    }
}
