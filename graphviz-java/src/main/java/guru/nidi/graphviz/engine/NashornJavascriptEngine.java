/*
 * Copyright © 2015 Stefan Niederhauser (nidin@gmx.ch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package guru.nidi.graphviz.engine;

import javax.script.*;

class NashornJavascriptEngine extends AbstractJavascriptEngine {
    private static final ScriptEngine ENGINE = new ScriptEngineManager().getEngineByName("nashorn");
    private final ScriptContext context = new SimpleScriptContext();
    private final ResultHandler resultHandler = new ResultHandler();

    NashornJavascriptEngine() {
        if (ENGINE == null) {
            throw new MissingDependencyException("Nashorn engine is not available", "org.openjdk.nashorn:nashorn-core");
        }
        context.getBindings(ScriptContext.ENGINE_SCOPE).put("handler", resultHandler);
        eval("function result(r){ handler.setResult(r); }"
                + "function error(r){ handler.setError(r); }"
                + "function log(r){ handler.log(r); }");
    }

    @Override
    protected String execute(String js) {
        eval(js);
        return resultHandler.waitFor();
    }

    private void eval(String js) {
        try {
            ENGINE.eval(js, context);
        } catch (ScriptException e) {
            throw new GraphvizException("Problem executing javascript", e);
        }
    }
}
