    package org.example;

    import java.io.IOException;
    import java.nio.charset.StandardCharsets;
    import java.nio.file.*;
    import java.util.*;

    public class Idiomas {
        public record Idioma(String codigo, List<String> textos, List<String> imagenes) {}

        public final Map<String, Idioma> mapa = new LinkedHashMap<>();

        public Idiomas(Path fichero) throws IOException {
            List<String> lineas = Files.readAllLines(fichero, StandardCharsets.UTF_8).stream()
                    .map(s -> s.replace("\uFEFF", "").strip())
                    .filter(s -> !s.isEmpty()).toList();
            Iterator<String> it = lineas.iterator();
            try {
                int n = Integer.parseInt(it.next());
                for (int i = 0; i < n; i++) {
                    String cod = it.next();
                    List<String> textos = new ArrayList<>();
                    for (int k = Integer.parseInt(it.next()); k > 0; k--) textos.add(it.next());
                    List<String> imgs = new ArrayList<>();
                    for (int k = Integer.parseInt(it.next()); k > 0; k--) imgs.add(it.next());
                    mapa.put(cod, new Idioma(cod, textos, imgs));
                }
            } catch (NoSuchElementException | NumberFormatException e) {
                throw new IOException("Formato de idiomas incorrecto: " + fichero, e);
            }
        }
    }