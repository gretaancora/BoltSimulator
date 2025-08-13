package org.pmcsn.utils;

import org.pmcsn.controller.FiniteImprovedSimulationRunner;
import org.pmcsn.controller.FiniteSimulationRunner;
import org.pmcsn.libraries.Rngs;
import org.pmcsn.model.Observations;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PlotUtils {
    private PlotUtils() {}

    public static void writeObservations(String path, List<Observations> observationsList) {
        System.out.println("[DEBUG] writeObservations for center="
                + observationsList.get(0).getCenterName()
                + " with obsCount=" + observationsList.size());

        FileUtils.createDirectoryIfNotExists(path);
        File parent = new File(path);
        List<Double> row = new ArrayList<>();

        // Trova la lunghezza massima tra tutte le osservazioni
        int maxSize = observationsList.stream()
                .mapToInt(o -> o.getPoints().size())
                .peek(sz -> System.out.println("  [DEBUG] points size=" + sz))
                .max()
                .orElseThrow(() -> new RuntimeException("No data to write!"));

        // Calcola la media per ogni indice, ignorando le liste più corte
        for (int i = 0; i < maxSize; i++) {
            double s = 0;
            int count = 0;
            for (Observations o : observationsList) {
                if (i < o.getPoints().size()) {
                    s += o.getPoints().get(i);
                    count++;
                }
            }
            row.add(count > 0 ? s / count : 0.0); // se nessun dato, metti 0
        }

        String centerName = observationsList.get(0).getCenterName();
        String[] parts = centerName.split("_");
        centerName = String.join("_", Arrays.copyOfRange(parts, 0, parts.length - 1));

        File file = new File(parent, "%s.data".formatted(centerName));
        writeRow(file, row);

        System.out.println("[DEBUG] wrote file " + file.getAbsolutePath()
                + " with " + row.size() + " columns");
    }

    private static void writeRow(File file, List<Double> points) {
        try (FileWriter fileWriter = new FileWriter(file, true)) {
            StringBuilder row = new StringBuilder();
            points.forEach(p -> row.append(p.toString()).append(","));
            fileWriter.write(row.append("\n").toString());
            fileWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        FileUtils.deleteDirectory("csvFiles/FINITE_SIMULATION");
        FileUtils.deleteDirectory("csvFiles/IMPROVED_FINITE_SIMULATION");
        Rngs rngs = new Rngs();
        rngs.selectStream(255);
        long[] seeds = new long[1];
        for (int i = 0; i < seeds.length; i++) {
            seeds[i] = (long) (rngs.random() * (Long.MAX_VALUE));
        }
        for (long seed : seeds) {
            FiniteSimulationRunner runner = new FiniteSimulationRunner(seed);
            runner.runFiniteSimulation(false, true);
            FiniteImprovedSimulationRunner iRunner = new FiniteImprovedSimulationRunner(seed);
            iRunner.runImprovedModelSimulation(false, true);
            PlotUtils.welchPlot("csvFiles/IMPROVED_FINITE_SIMULATION/%d/observations".formatted(seed));
            PlotUtils.welchPlot("csvFiles/FINITE_SIMULATION/%d/observations".formatted(seed));
        }
    }

    public static void welchPlot(String parent) throws IOException {
        List<Path> files = listAllFiles(Path.of(parent));
        for (Path file : files) {
            List<List<Double>> matrix = new ArrayList<>();
            for (String line : Files.readAllLines(file)) {
                if (line.trim().split(",").length >= 30) {
                    matrix.add(Arrays.stream(line.trim().split(","))
                            .mapToDouble(Double::parseDouble)
                            .boxed()
                            .collect(Collectors.toList()));
                }
            }
            List<Double> plot = finiteSimulationPlot(matrix);
            String plotPath = file.toString().replace(".data", "_plot.csv");
            savePlot(plotPath, plot);
        }
    }

    /*private static List<Double> finiteSimulationPlot(List<List<Double>> matrix) {
        int minSize = matrix.stream().mapToInt(List::size).min().orElseThrow();
        List<Double> averages = new ArrayList<>();
        for (int i = 0; i < minSize; i++) {
            double sum = 0.0;
            for (List<Double> doubles : matrix) {
                sum += doubles.get(i);
            }
            averages.add(sum / matrix.size());
        }
        return averages;
    }*/

    private static List<Double> finiteSimulationPlot(List<List<Double>> matrix) {
        // Controlli preliminari
        if (matrix == null || matrix.isEmpty()) {
            return Collections.emptyList();
        }

        // Trova la dimensione minima delle righe; se nessuna riga, minSize = 0
        int minSize = matrix.stream()
                .mapToInt(List::size)
                .min()
                .orElse(0);

        // Se la dimensione minima è zero, non ci sono dati effettivi per la media
        if (minSize == 0) {
            return Collections.emptyList();
        }

        // Calcolo delle medie colonna per colonna
        List<Double> averages = new ArrayList<>(minSize);
        for (int i = 0; i < minSize; i++) {
            double sum = 0.0;
            for (List<Double> row : matrix) {
                sum += row.get(i);
            }
            averages.add(sum / matrix.size());
        }

        return averages;
    }

    private static void savePlot(String plotPath, List<Double> plot) {
        try (FileWriter w = new FileWriter(plotPath)) {
            StringBuilder s = new StringBuilder();
            s.append("E[Ts]\n");
            plot.forEach(x -> s.append(x).append("\n"));
            w.write(s.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Path> listAllFiles(Path parent) throws IOException {
        List<Path> paths = new ArrayList<>();
        paths.add(parent);
        List<Path> files = new ArrayList<>();
        while (!paths.isEmpty()) {
            var path = paths.removeLast();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path))
            {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        if (!Files.isHidden(entry)) {
                            paths.add(entry);
                        }
                    } else if (entry.toString().endsWith(".data")) {
                        files.add(entry);
                    }
                }
            }
        }
        return files;
    }
}