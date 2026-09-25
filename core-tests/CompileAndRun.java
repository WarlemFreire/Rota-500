import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class CompileAndRun {
    public static void main(String[] args) throws Exception {
        Path project = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        Path output = project.resolve("build/core-tests");
        Files.createDirectories(output);

        List<File> sources = new ArrayList<>();
        try (var stream = Files.list(project.resolve("app/src/main/java/br/com/soswf/rota500/core"))) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .forEach(sources::add);
        }
        sources.add(project.resolve("core-tests/src/br/com/soswf/rota500/core/CoreSelfTest.java").toFile());

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Módulo de compilação Java indisponível");
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            var units = fileManager.getJavaFileObjectsFromFiles(sources);
            boolean ok = compiler.getTask(null, fileManager, null,
                    List.of("-d", output.toString(), "--release", "17"), null, units).call();
            if (!ok) throw new IllegalStateException("Falha na compilação do núcleo");
        }

        try (URLClassLoader loader = new URLClassLoader(new URL[]{output.toUri().toURL()})) {
            Class<?> test = loader.loadClass("br.com.soswf.rota500.core.CoreSelfTest");
            test.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        }
    }
}
