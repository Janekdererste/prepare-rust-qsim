package org.matsim.prepare;

import com.beust.jcommander.Parameter;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PrepareRustScalingExperiment {

    public static class InputArgs {

        @Parameter(names = "-c", required = true)
        public Path config;

        @Parameter(names = "-e", required = true)
        public Path events;

        @Parameter(names = "-o", required = true)
        public Path outputDirectory;

        @Parameter(names = "-sss", required = true)
        public double sourceSampleSize;

        @Parameter(names = "-tss", required = true)
        public double targetSampleSize;

        @Parameter(names = "-wn", required = false)
        public boolean writeNetwork = false;
    }

    public static void main(String[] args) {

        var inputArgs = new InputArgs();
        com.beust.jcommander.JCommander.newBuilder()
                .addObject(inputArgs)
                .build()
                .parse(args);

        var config = ConfigUtils.loadConfig(inputArgs.config.toString());
        var plansFile = Paths.get(config.getContext().toString()).getParent().resolve(config.plans().getInputFile());
        config.plans().setInputFile(null);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.travelTimeCalculator().setMaxTime(144000); // 40 hours
        var scenario = ScenarioUtils.loadScenario(config);

        var factor = inputArgs.targetSampleSize / inputArgs.sourceSampleSize;
        var sizeName = String.format("%.1f", inputArgs.targetSampleSize * 100);
        var outPath = inputArgs.outputDirectory.resolve(config.controler().getRunId() + "-" + sizeName + "pct.plans.xml.gz");
        var writer = new StreamingPopulationWriter(factor);
        writer.startStreaming(outPath.toString());
        var reader = new StreamingPopulationReader(scenario);

        if (factor > 1.0) {
            var upscaleAlgorithm = SimpleUpscaleAlgorithm.create(
                    inputArgs.targetSampleSize / inputArgs.sourceSampleSize,
                    inputArgs.events.toString(), scenario, writer
            );
            var filter = new PtFilter(upscaleAlgorithm, scenario);
            reader.addAlgorithm(filter);
        } else {
            reader.addAlgorithm(new PtFilter(writer, scenario));
        }

        reader.readFile(plansFile.toString());
        writer.closeStreaming();

        if (inputArgs.writeNetwork) {
            PrepareRustQSimScenario.removeLinks(scenario, TransportMode.pt);
            var netOutPath = inputArgs.outputDirectory.resolve(config.controler().getRunId() + ".network.xml.gz");
            NetworkUtils.writeNetwork(scenario.getNetwork(), netOutPath.toString());
        }
    }
}
