package org.dataflowanalysis.converter.tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.dataflowanalysis.converter.MicroSecEndConverter;

public class TUHHPipeline {

    public static Path converter;

    @Disabled
    @Test
    public void runPipeline() throws IOException {
        var tuhhRepo = "microSecEnD-main";
        assertTrue(Files.isDirectory(Paths.get(tuhhRepo)));

        var converterRepo = Paths.get(tuhhRepo, "convert_model.py");
        assertTrue(Files.isRegularFile(converterRepo));

        Path datasetFolderRepo = Paths.get(tuhhRepo, "dataset");
        List<Path> datasetsRepo = new ArrayList<>();
        Files.list(datasetFolderRepo)
                .forEach(path -> {
                    if (Files.isDirectory(path)) {
                        datasetsRepo.add(path);
                    }
                });
        assertEquals(datasetsRepo.size(), 17);

        var tuhh = Paths.get("TUHH-Models");
        removeAndCreateDir(tuhh);

        converter = tuhh.resolve(converterRepo.getFileName());
        Files.copy(converterRepo, converter, StandardCopyOption.REPLACE_EXISTING);

        List<Path> datasets = new ArrayList<>();
        for (var dataset : datasetsRepo) {
            var datasetName = dataset.getFileName()
                    .toString();
            int underscoreIndex = datasetName.indexOf('_');
            if (datasetName.contains("kafka")) {
                datasetName = datasetName.substring(0, underscoreIndex) + "-kafka";
            } else {
                datasetName = datasetName.substring(0, underscoreIndex);
            }
            var newDataset = tuhh.resolve(datasetName);
            copyDir(dataset.toString(), newDataset.toString());
            datasets.add(newDataset);
        }

        for (var dataset : datasets) {
            cleanTopLevelOfDataset(dataset);
            renameTxtVariants(dataset.resolve("model_variants"));
            moveTxtVariantsUp(dataset.resolve("model_variants"));
            convertTxtToJson(dataset);
            convertJsonToDFD(dataset);
        }

        Files.delete(converter);
    }

    private void convertJsonToDFD(Path dataset) throws IOException {
        var microConverter = new MicroSecEndConverter();
        Files.list(dataset)
                .forEach(path -> {
                    if (Files.isRegularFile(path) && path.toString()
                            .endsWith(".json")) {
                        System.out.println(path);
                        var complete = microConverter.microToDfd(path.toString());
                        microConverter.storeDFD(complete, path.toString());
                    }
                });
    }

    private void convertTxtToJson(Path dataset) throws IOException {
        Files.list(dataset)
                .forEach(path -> {
                    if (Files.isRegularFile(path)) {
                        if (path.toString()
                                .endsWith(".txt")) {
                            System.out.println(path);
                            try {
                                runPythonScript(converter.toString(), path.toString(), "json", path.toString()
                                        .replace(".txt", ".json"));
                                Files.delete(path);
                            } catch (InterruptedException | IOException e) {
                            }
                        }
                    }
                });
    }

    private void moveTxtVariantsUp(Path dir) throws IOException {
        Path parentDir = dir.getParent();

        Files.list(dir)
                .forEach(path -> {
                    if (Files.isRegularFile(path)) {
                        Path targetPath = parentDir.resolve(path.getFileName());
                        try {
                            Files.move(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                        }
                    }
                });

        Files.delete(dir);
    }

    private void cleanTopLevelOfDataset(Path dataset) throws IOException {
        Files.list(dataset)
                .forEach(path -> {
                    if (Files.isRegularFile(path)) {
                        if (!path.toString()
                                .endsWith(".json") || path.toString()
                                        .endsWith("traceability.json")) {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                            }
                        } else {
                            var renamedBaseModel = dataset.resolve(dataset.getFileName() + "_0.json");
                            try {
                                Files.move(path, renamedBaseModel, StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                            }
                        }
                    }
                });
    }

    private void removeAndCreateDir(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                        }
                    });
        }
        Files.createDirectories(dir);
    }

    private void copyDir(String sourceDirectoryLocation, String destinationDirectoryLocation) throws IOException {
        Files.walk(Paths.get(sourceDirectoryLocation))
                .forEach(source -> {
                    Path destination = Paths.get(destinationDirectoryLocation, source.toString()
                            .substring(sourceDirectoryLocation.length()));
                    try {
                        Files.copy(source, destination);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    private void renameTxtVariants(Path variants) throws IOException {
        var modelName = variants.getParent()
                .getFileName();
        Files.list(variants)
                .forEach(path -> {
                    if (Files.isRegularFile(path)) {
                        var renamedModel = variants.resolve(modelName + "_" + path.getFileName());
                        try {
                            Files.move(path, renamedModel, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                        }
                    }
                });
    }

    private int runPythonScript(String script, String in, String format, String out) throws InterruptedException, IOException {
        String[] command = {"python3", script, in, format, "-op", out};

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process;

        process = processBuilder.start();
        return process.waitFor();

    }
}