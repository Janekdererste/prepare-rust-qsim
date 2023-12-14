package org.matsim.prepare;

import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class PrepareRustQSimScenario {

    public static void main(String[] args) {

        var config = ConfigUtils.createConfig();
        config.network().setInputFile("/Users/janek/Documents/rust_q_sim/berlin/input/berlin-v6.0-network-with-pt.xml.gz");
        config.facilities().setInputFile("/Users/janek/Documents/rust_q_sim/berlin/input/004.output_facilities.xml.gz");
        config.global().setCoordinateSystem("EPSG:25832");
        var scenario = ScenarioUtils.loadScenario(config);
        var net = scenario.getNetwork();
        var facilities = scenario.getActivityFacilities();

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
        reader.readFile("/Users/janek/Documents/rust_q_sim/berlin/input/004.output_plans.xml.gz");
        writer25pct.closeStreaming();
        writer1pct.closeStreaming();
        writer01pct.closeStreaming();

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

        NetworkUtils.writeNetwork(net, "/Users/janek/Documents/rust_q_sim/berlin/input/berlin.network.xml.gz");
    }
}
