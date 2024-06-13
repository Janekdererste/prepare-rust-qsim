package org.matsim.prepare;


import com.beust.jcommander.Parameter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public class PrepareRustQSimScenario {

    private static final Logger log = LogManager.getLogger(PrepareRustQSimScenario.class);

    public static class InputArgs {

        @Parameter(names = "-c", required = true)
        public Path config;

        @Parameter(names = "-e", required = true)
        public Path events;

        @Parameter(names = "-o", required = true)
        public Path outputDirectory;

        @Parameter(names = "-f")
        public double factor = 10.;

        @Parameter(names = "-ss")
        public List<Double> sampleSizes = List.of(1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0.01, 0.001);
    }

    private static Collection<StreamingPopulationWriter> createUpscaleWriters(Collection<Double> samplesSizes, Path outputDir, String runId) {
        return samplesSizes.stream()
                .map(size -> {
                    var sizeName = Math.round(size * 100);
                    var outPath = outputDir.resolve(runId + "-" + sizeName + "pct.plans.xml.gz");
                    var writer = new StreamingPopulationWriter(size);
                    writer.startStreaming(outPath.toString());
                    return writer;
                })
                .toList();
    }

    public static void main(String[] args) {

        var inputArgs = new InputArgs();

        com.beust.jcommander.JCommander.newBuilder()
                .addObject(inputArgs)
                .build()
                .parse(args);

        var config = ConfigUtils.loadConfig(inputArgs.config.toString());
        var plansFile = config.plans().getInputFile();
        config.plans().setInputFile(null);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.travelTimeCalculator().setMaxTime(144000); // 40 hours
        var scenario = ScenarioUtils.loadScenario(config);

        var writers = createUpscaleWriters(inputArgs.sampleSizes, inputArgs.outputDirectory, config.controler().getRunId());
        var reader = new StreamingPopulationReader(scenario);
        reader.addAlgorithm(UpscaleAlgorithm.create(inputArgs.factor, inputArgs.events.toString(), scenario, writers));
        reader.readFile(plansFile);

        for (var writer : writers) {
            writer.closeStreaming();
        }

        removeLinks(scenario, TransportMode.pt);
        var netOutPath = inputArgs.outputDirectory.resolve(config.controler().getRunId() + ".network.xml.gz");
        NetworkUtils.writeNetwork(scenario.getNetwork(), netOutPath.toString());
    }

    public static void removeLinks(Scenario scenario, String mode) {
        // filter pt from network and make networks smaller for testing
        var net = scenario.getNetwork();
        var ptLinks = net.getLinks().values().parallelStream()
                .filter(link -> link.getAllowedModes().contains(mode))
                .map(Identifiable::getId)
                .toList();
        for (var link : ptLinks) {
            net.removeLink(link);
        }

        var emtpyNodes = net.getNodes().values().parallelStream()
                .filter(node -> node.getInLinks().isEmpty() && node.getOutLinks().isEmpty())
                .map(Identifiable::getId)
                .toList();
        for (var node : emtpyNodes) {
            net.removeNode(node);
        }
    }
}
