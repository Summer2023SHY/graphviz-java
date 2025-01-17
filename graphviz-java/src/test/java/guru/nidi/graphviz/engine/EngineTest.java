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

import com.eclipsesource.v8.V8;
import guru.nidi.graphviz.service.CommandLineExecutor;
import guru.nidi.graphviz.service.SystemUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static guru.nidi.graphviz.engine.Format.SVG;
import static guru.nidi.graphviz.engine.Format.SVG_STANDALONE;
import static guru.nidi.graphviz.engine.FormatTest.START1_7;
import static guru.nidi.graphviz.engine.GraphvizCmdLineEngine.FdpOption;
import static guru.nidi.graphviz.engine.GraphvizCmdLineEngine.NeatoOption;
import static guru.nidi.graphviz.model.Factory.graph;
import static guru.nidi.graphviz.model.Factory.node;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Every.everyItem;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class EngineTest {
    @Nullable
    private static File temp;

    @BeforeAll
    static void init() throws IOException {
        temp = new File(System.getProperty("java.io.tmpdir"), "engineTest");
        FileUtils.deleteDirectory(temp);
        temp.mkdir();
    }

    @AfterEach
    void end() {
        Graphviz.releaseEngine();
    }

    @Test
    void jdk() {
        Graphviz.useEngine(new GraphvizJdkEngine());
        assertThat(Graphviz.fromString("graph g {a--b}").render(SVG_STANDALONE).toString(), startsWith(START1_7));
    }

    @Test
    void server() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"), "I gave up fixing this");
        GraphvizServerEngine.stopServer(34567);
        try {
            Graphviz.useEngine(new GraphvizServerEngine().port(34567).useEngine(new GraphvizV8Engine()));
            assertThat(Graphviz.fromString("graph g {a--b}").render(SVG_STANDALONE).toString(), startsWith(START1_7));
        } finally {
            GraphvizServerEngine.stopServer(34567);
        }
    }

    @Test
    void v8() {
        Graphviz.useEngine(new GraphvizV8Engine());
        assertThat(Graphviz.fromString("graph g {a--b}").render(SVG_STANDALONE).toString(), startsWith(START1_7));
    }

    @Test
    void v8WithoutPath() throws Exception {
        assertNativeLibs(System.getProperty("user.home"), () -> Graphviz.useEngine(new GraphvizV8Engine()));
    }

    @Test
    void v8WithPath() throws Exception {
        final String tmpDir = System.getProperty("java.io.tmpdir");
        assertNativeLibs(tmpDir, () -> Graphviz.useEngine(new GraphvizV8Engine(tmpDir)));
    }

    private void assertNativeLibs(String basedir, Runnable task) throws ReflectiveOperationException {
        final File[] libs = new File[]{
                new File(basedir, "libj2v8_linux_x86_64.so"),
                new File(basedir, "libj2v8_macosx_x86_64.dylib"),
                new File(basedir, "libj2v8_win32_x86.dll"),
                new File(basedir, "libj2v8_win32_x86_64.dll"),
        };
        for (final File lib : libs) {
            lib.delete();
        }
        final Field loaded = V8.class.getDeclaredField("nativeLibraryLoaded");
        loaded.setAccessible(true);
        loaded.setBoolean(null, false);
        task.run();
        for (final File lib : libs) {
            if (lib.exists()) {
                return;
            }
        }
        fail("No native library found");
    }

    @ParameterizedTest
    @MethodSource
    void multi(Supplier<GraphvizEngine> engineSupplier) throws InterruptedException {
        Graphviz.useEngine(engineSupplier.get());
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final List<String> res = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            res.add(null);
            final int j = i;
            executor.submit(() -> {
                res.set(j, Graphviz.fromString("graph g {number" + j + "--b}").render(SVG).toString());
                Graphviz.releaseEngine();
            });
        }
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
        assertThat(res, everyItem(not(isEmptyOrNullString())));
        for (int i = 0; i < res.size(); i++) {
            assertThat(res.get(i), containsString("number" + i));
        }
    }

    static List<Supplier<GraphvizEngine>> multi() {
        return asList(GraphvizV8Engine::new, GraphvizJdkEngine::new);
    }

    @Test
    void cmdLine() throws IOException, InterruptedException {
        Graphviz.useEngine(new GraphvizCmdLineEngine("dot")
                .searchPath(fakeDotFile().getParent())
                .executor(fileCommandExecutor()));

        final String actual = Graphviz.fromString("graph g {a--b}").render(SVG_STANDALONE).toString();
        assertThat(actual, startsWith(START1_7.replace("\n", System.lineSeparator())));
    }

    @Test
    void cmdLineNeato() throws IOException, InterruptedException {
        Graphviz.useEngine(new GraphvizCmdLineEngine("dot", NeatoOption.NO_LAYOUT_ALLOW_OVERLAP, NeatoOption.REDUCE_GRAPH)
                .searchPath(fakeDotFile().getParent())
                .executor(argumentsCommandExecutor()));

        final File file = new File("target/out.svg");
        Graphviz.fromString("graph g {a--b}").render(SVG).toFile(file);
        assertThat(new String(Files.readAllBytes(file.toPath()), UTF_8), containsString("-n2 -x"));
    }

    @Test
    void cmdLineFdp() throws IOException, InterruptedException {
        Graphviz.useEngine(new GraphvizCmdLineEngine("dot",
                FdpOption.NO_GRID, FdpOption.OLD_FORCE, FdpOption.overlapExpansionFactor(1.2), FdpOption.iterations(3),
                FdpOption.unscaledFactor(2.3), FdpOption.temperature(42))
                .searchPath(fakeDotFile().getParent())
                .executor(argumentsCommandExecutor()));

        final File file = new File("target/out.svg");
        Graphviz.fromString("graph g {a--b}").render(SVG).toFile(file);
        assertThat(new String(Files.readAllBytes(file.toPath()), UTF_8), containsString("-Lg -LO -LC1.2 -Ln3 -LU2.3 -LT42.0"));
    }

    @Test
    void cmdLineBuiltInRasterizer() throws IOException, InterruptedException {
        Graphviz.useEngine(new GraphvizCmdLineEngine("dot")
                .searchPath(fakeDotFile().getParent())
                .executor(argumentsCommandExecutor()));

        final File file = new File("target/out.svg");
        Graphviz.fromString("graph g {a--b}").rasterize(Rasterizer.builtIn("svg", "render", "format")).toFile(file);
        assertThat(new String(Files.readAllBytes(file.toPath()), UTF_8), containsString("-Tsvg:render:format"));
    }

    /**
     * Test to check if we can set the output path and name of the dot file
     */
    @Test
    void cmdLineOutputDotFile() throws IOException, InterruptedException {
        final File dotOutputFolder = new File(temp, "out");
        dotOutputFolder.mkdir();
        final String dotOutputName = "test123";

        // Configure engine to output the dotFile to dotOutputFolder
        final GraphvizCmdLineEngine engine = new GraphvizCmdLineEngine()
                .searchPath(fakeDotFile().getParent())
                .executor(fileCommandExecutor());
        engine.setDotOutputFile(dotOutputFolder.getAbsolutePath(), dotOutputName);

        Graphviz.useEngine(engine);

        // Do execution
        Graphviz.fromString("graph g {a--b}").render(SVG_STANDALONE).toString();

        assertTrue(new File(dotOutputFolder.getAbsolutePath(), dotOutputName + ".dot").exists());
    }

    @Test
    void escapeAmpersand() {
        assertThat(Graphviz.fromGraph(graph().with(node("Z&bl;g"))).render(SVG).toString(), containsString(">Z&amp;bl;g<"));
    }

    @Test
    void escapeSubSpace() {
        assertThat(Graphviz.fromGraph(graph().with(node("Z\u0001a\u001fg"))).render(SVG).toString(), containsString(">Z a g<"));
    }

    private File fakeDotFile() throws IOException {
        final String filename = SystemUtils.executableNames("dot").get(0);
        final File dotFile = new File(temp, filename);
        dotFile.createNewFile();
        dotFile.setExecutable(true);
        return dotFile;
    }

    private CommandLineExecutor fileCommandExecutor() throws IOException, InterruptedException {
        final CommandLineExecutor cmdExecutor = mock(CommandLineExecutor.class);
        doAnswer(invocation -> {
            final File workingDirectory = invocation.getArgument(1);
            final File svgInput = new File(getClass().getClassLoader().getResource("outfile1.svg").getFile());
            final File svgOutput = new File(workingDirectory.getAbsolutePath() + "/outfile.svg");
            Files.copy(svgInput.toPath(), svgOutput.toPath());
            return null;
        }).when(cmdExecutor).execute(any(CommandLine.class), any(File.class), any(Integer.class));
        return cmdExecutor;
    }

    private CommandLineExecutor argumentsCommandExecutor() throws IOException, InterruptedException {
        final CommandLineExecutor cmdExecutor = mock(CommandLineExecutor.class);
        doAnswer(invocation -> {
            final File workingDirectory = invocation.getArgument(1, File.class);
            try (final Writer out = new OutputStreamWriter(new FileOutputStream(new File(workingDirectory.getAbsolutePath() + "/outfile.svg")), UTF_8)) {
                out.write(Arrays.toString(invocation.getArgument(0, CommandLine.class).getArguments()));
            }
            return null;
        }).when(cmdExecutor).execute(any(CommandLine.class), any(File.class), any(Integer.class));
        return cmdExecutor;
    }
}
