import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;
import com.sun.source.util.JavacTask;

public class SyntaxCheck {
    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]);
        List<Path> javaFiles = new ArrayList<>();
        try (var stream = Files.walk(project.resolve("app/src/main/java"))) {
            stream.filter(path -> path.toString().endsWith(".java")).forEach(javaFiles::add);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> units = manager.getJavaFileObjectsFromPaths(javaFiles);
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "-source", "17"), null, units);
            task.parse();
        }
        int errors = 0;
        for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                errors++;
                System.err.println(diagnostic);
            }
        }
        if (errors > 0) throw new IllegalStateException(errors + " erros de sintaxe Java");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        int xmlCount = 0;
        try (var stream = Files.walk(project.resolve("app/src/main"))) {
            for (Path path : stream.filter(value -> value.toString().endsWith(".xml")).toList()) {
                factory.newDocumentBuilder().parse(path.toFile());
                xmlCount++;
            }
        }
        System.out.println("SyntaxCheck: " + javaFiles.size() + " Java e " + xmlCount + " XML válidos");
    }
}
