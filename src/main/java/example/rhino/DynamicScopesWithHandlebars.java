package example.rhino;

/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import org.apache.commons.io.IOUtils;
import org.mozilla.javascript.*;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DynamicScopesWithHandlebars {

    public static final int NUM_THREAD = 20;

    private static class MyFactory extends ContextFactory {
        @Override
        protected boolean hasFeature(Context cx, int featureIndex) {
            return featureIndex == Context.FEATURE_DYNAMIC_SCOPE || super.hasFeature(cx, featureIndex);
        }
    }

    static {
        ContextFactory.initGlobal(new MyFactory());
    }


    public static void main(String[] args) {
        Context cx = Context.enter();
        try {
            String source = handlebars();
            Script script = cx.compileString(source, "handlebars", 1, null);

            System.out.println("Running the script in a single thread");
            runScripts(cx, script, Executors.newSingleThreadExecutor());

            int nThreads = Runtime.getRuntime().availableProcessors();
            System.out.format("Running the script in %d thread", nThreads);

            runScripts(cx, script, Executors.newFixedThreadPool(nThreads));

        } catch (IOException e) {
            System.err.println("No handlebars file found");
        } finally {
            Context.exit();
        }
    }

    private static String handlebars() throws IOException {
        return readFile("handlebars-1.0.0.js");
    }

    private static String readFile(String name) throws IOException {
        return IOUtils.toString(DynamicScopesWithHandlebars.class.getResource(name).openStream());
    }

    private static void runScripts(Context cx, Script script, ExecutorService executor) {
        ScriptableObject sharedScope = cx.initStandardObjects(null, true);

        script.exec(cx, sharedScope);

        Runnable[] t = new Runnable[NUM_THREAD];
        for (int i = 0; i < NUM_THREAD; i++) {

            String source2 = "Handlebars.precompile(template)";

            t[i] = new PerThread(sharedScope, source2, i);
        }
        for (int i = 0; i < NUM_THREAD; i++) {
            executor.execute(t[i]);
        }
        executor.shutdown();
    }

    static class PerThread implements Runnable {

        private static final int NUM_OF_TEMPLATE_AVAILABLE = 6;

        PerThread(Scriptable sharedScope, String source, int threadId) {
            this.sharedScope = sharedScope;
            this.source = source;
            this.threadId = threadId;
        }

        public void run() {
            Context cx = Context.enter();
            String fileName = getFileName(threadId);

            try {
                Scriptable threadScope = cx.newObject(sharedScope);
                threadScope.setPrototype(sharedScope);

                threadScope.setParentScope(null);

                String fileContent = readFile(fileName);
                threadScope.put("template", threadScope, fileContent);

                cx.evaluateString(threadScope, source, "threadScript", 1, null);
            } catch (RhinoException e) {
                System.err.format("Error executing %s", fileName);
                e.printStackTrace();
            } catch (IOException e) {
                System.err.println("No template file found");
                System.exit(-1);
            } finally {
                Context.exit();
            }
        }

        private String getFileName(int threadId) {
            return String.format("template-0%d.hbs", ((threadId % NUM_OF_TEMPLATE_AVAILABLE) + 1));
        }

        private Scriptable sharedScope;
        private String source;
        private int threadId;
    }

}