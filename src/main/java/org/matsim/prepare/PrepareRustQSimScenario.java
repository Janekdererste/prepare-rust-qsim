package org.matsim.prepare;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.SearchableNetwork;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.geotools.MGC;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class PrepareRustQSimScenario {

    public static void main(String[] args) {

        var scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        StreamingPopulationReader reader = new StreamingPopulationReader(scenario);
        StreamingPopulationWriter writer25pct = new StreamingPopulationWriter();
        writer25pct.startStreaming("/Users/janek/Documents/rust_q_sim/berlin/input/berlin-25pct.plans.xml.gz");
        StreamingPopulationWriter writer1pct = new StreamingPopulationWriter();
        writer1pct.startStreaming("/Users/janek/Documents/rust_q_sim/berlin/input/berlin-1pct.plans.xml.gz");
        StreamingPopulationWriter writer01pct = new StreamingPopulationWriter();
        writer01pct.startStreaming("/Users/janek/Documents/rust_q_sim/berlin/input/berlin-01pct.plans.xml.gz");
        StreamingPopulationWriter writerSingle = new StreamingPopulationWriter();
        writerSingle.startStreaming("/Users/janek/Documents/rust_q_sim/berlin/input/berlin-single.plans.xml.gz");
        var rand = new Random();
        AtomicInteger personCounter = new AtomicInteger();

        reader.addAlgorithm(person -> {

            var selectedPlan = person.getSelectedPlan();

            // filter out pt plans
            var hashPt = selectedPlan.getPlanElements().stream()
                    .filter(e -> e instanceof Leg)
                    .map(e -> (Leg) e)
                    .anyMatch(l -> l.getMode().equals(TransportMode.pt));
            if (hashPt) return;

            person.getPlans().clear();
            person.addPlan(selectedPlan);

            // write the first person into the single agent file
            if (personCounter.get() == 0) {
                writerSingle.writePerson(person);
                writerSingle.closeStreaming();
            }

            // draw 10% sample and 1% sample (out of 10% gives 1% and 0.1% samples)
            var randNum = rand.nextDouble();
            if (randNum >= 0.96) {
                writer1pct.writePerson(person);
            }
            if (randNum >= 0.996) {
                writer01pct.writePerson(person);
            }

            // write all persons with only selected plan to 10% sample
            writer25pct.writePerson(person);
            personCounter.getAndIncrement();
        });
        reader.readFile("/Users/janek/Documents/rust_q_sim/berlin/input/berlin-v6.0-25pct.plans.xml.gz");
        writer25pct.closeStreaming();
        writer1pct.closeStreaming();
        writer01pct.closeStreaming();

        // filter pt from network and make networks smaller for testing
        var net = NetworkUtils.readNetwork("/Users/janek/Documents/rust_q_sim/berlin/input/berlin-v6.0-network-with-pt.xml.gz");
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

        NetworkUtils.writeNetwork(net, "/Users/janek/Documents/rust_q_sim/berlin/input/berlin.network.xml.gz");
    }
}
