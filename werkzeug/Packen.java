import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Packt einen Ordner mit .class-Dateien in eine Jar (Ersatz fuer das jar-Programm, das DataGrips Java nicht mitbringt). */
public class Packen {
    public static void main(String[] args) throws IOException {
        Path ordner = Paths.get(args[0]);
        Path jar = Paths.get(args[1]);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "at.oida.Werkzeug");
        List<Path> dateien;
        try (Stream<Path> s = Files.walk(ordner)) {
            dateien = s.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
        }
        try (OutputStream aus = Files.newOutputStream(jar); JarOutputStream j = new JarOutputStream(aus, manifest)) {
            for (Path d : dateien) {
                j.putNextEntry(new JarEntry(ordner.relativize(d).toString().replace(java.io.File.separatorChar, '/')));
                Files.copy(d, j);
                j.closeEntry();
            }
        }
        System.out.println(jar.getFileName() + ": " + dateien.size() + " Dateien");
    }
}
