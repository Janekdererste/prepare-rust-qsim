package org.matsim.prepare;


import com.beust.jcommander.Parameter;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilities;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

public class PrepareRustQSimScenario {


    public static class InputArgs {

        @Parameter(names = "-n", required = true)
        public Path network;

        @Parameter(names = "-p", required = true)
        public Path population;

        @Parameter(names = "-f")
        public Path facilities;

        @Parameter(names = "-o", required = true)
        public Path outputDirectory;

        @Parameter(names = "-r", required = true)
        public String runId;

        @Parameter(names = "-s")
        public double sampleSize = 0.1;

        @Parameter(names = "-ts")
        public List<Double> targetSampleSizes = List.of(0.1, 0.01, 0.001);
    }

    public record SampledWriter(double propability, StreamingPopulationWriter writer){}

    public static void main(String[] args) {

        var inputArgs = new InputArgs();

        com.beust.jcommander.JCommander.newBuilder()
                .addObject(inputArgs)
                .build()
                .parse(args);

        var config = ConfigUtils.createConfig();
        config.network().setInputFile(inputArgs.network.toString());
        var facilitiesPath = inputArgs.facilities != null ? inputArgs.facilities.toString() : null;
        config.facilities().setInputFile(facilitiesPath);
        config.global().setCoordinateSystem("EPSG:25832");
        var scenario = ScenarioUtils.loadScenario(config);
        var net = scenario.getNetwork();
        var facilities = scenario.getActivityFacilities();

        var writers = inputArgs.targetSampleSizes.stream()
                .map(size -> {
                    var probability = size / inputArgs.sampleSize;
                    var sizeName = Math.round(size * 100);
                    var outPath = inputArgs.outputDirectory.resolve(inputArgs.runId + "-" + sizeName + "pct.plans.xml.gz");
                    var writer = new StreamingPopulationWriter();
                    writer.startStreaming(outPath.toString());
                    return new SampledWriter(probability, writer);
                })
                .toList();

        var reader = getStreamingPopulationReader(scenario, facilities, writers);

        reader.readFile(inputArgs.population.toString());
        for (var writer : writers) {
            writer.writer.closeStreaming();
        }

        // filter pt from network and make networks smaller for testing
        var ptLinks = net.getLinks().values().parallelStream()
                .filter(link -> link.getAllowedModes().contains(TransportMode.pt))
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

        var netOutPath = inputArgs.outputDirectory.resolve(inputArgs.runId + ".network.xml.gz");
        NetworkUtils.writeNetwork(net, netOutPath.toString());
    }

    private static StreamingPopulationReader getStreamingPopulationReader(Scenario scenario, ActivityFacilities facilities, List<SampledWriter> writers) {
        var reader = new StreamingPopulationReader(scenario);
        var rand = new Random();

        reader.addAlgorithm(person -> {
            var selectedPlan = person.getSelectedPlan();
            for (PlanElement e : selectedPlan.getPlanElements()) {
                if (e instanceof Activity a) {
                    if (a.getLinkId() == null) {
                        var facility = facilities.getFacilities().get(a.getFacilityId());

                        System.out.println("Found Empty activity. Adding Coord: " + facility.getCoord() + " and link-id:" + facility.getLinkId() + " to activity");
                        a.setCoord(facility.getCoord());
                        a.setLinkId(facility.getLinkId());
                    }
                } else if (e instanceof Leg l) {
                    // filter out pt plans
                    if (l.getMode().equals(TransportMode.pt)) return;
                }
            }

            person.getPlans().clear();
            person.addPlan(selectedPlan);
            var randNum = rand.nextDouble();
            for (var writer : writers) {
                if (writer.propability >= randNum) {
                    writer.writer.writePerson(person);
                }
            }
        });
        return reader;
    }
}
