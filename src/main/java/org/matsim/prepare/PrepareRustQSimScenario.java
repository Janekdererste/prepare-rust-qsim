package org.matsim.prepare;

import org.geotools.geometry.jts.LiteCoordinateSequenceFactory;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
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
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;

import java.nio.channels.AcceptPendingException;
import java.util.*;
import java.util.stream.Stream;

public class PrepareRustQSimScenario {

    public static void main(String[] args) {

        var pop = PopulationUtils.readPopulation("/Users/janek/Documents/rust_q_sim/input/010.output_plans.xml.gz");//scenario.getPopulation(); //PopulationUtils.readPopulation("/Users/janek/projects/matsim-libs/examples/scenarios/berlin/plans_hwh_1pct.xml.gz");
        var ptPersons = pop.getPersons().values().parallelStream()
                .filter(p -> {
                    var plan = p.getSelectedPlan();
                    return plan.getPlanElements().stream()
                            .filter(e -> e instanceof Leg)
                            .map(e -> (Leg)e)
                            .anyMatch(l -> l.getMode().equals(TransportMode.pt));
                })
                .toList();

        for (var person : ptPersons) {
            pop.removePerson(person.getId());
        }

        var population1pct = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        var population01pct = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        var populationSingle = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        var rand = new Random();

        for (var person : pop.getPersons().values()) {
            var selectedPlan = person.getSelectedPlan();
            person.getPlans().clear();
            person.addPlan(selectedPlan);

            if (populationSingle.getPersons().isEmpty()) {
                populationSingle.addPerson(person);
            }

            var num = rand.nextDouble();
            if (num >= 0.9) {
                population1pct.addPerson(person);
            }

            if (num >= 0.9) {
                population01pct.addPerson(person);
            }
        }

        PopulationUtils.writePopulation(populationSingle, "/Users/janek/Documents/rust_q_sim/input/rvr-single.plans.xml.gz");
        PopulationUtils.writePopulation(population01pct,"/Users/janek/Documents/rust_q_sim/input/rvr-1pct.plans.xml.gz");
        PopulationUtils.writePopulation(population1pct, "/Users/janek/Documents/rust_q_sim/input/rvr-01pct.plans.xml.gz");
        PopulationUtils.writePopulation(pop, "/Users/janek/Documents/rust_q_sim/input/rvr.plans.xml.gz");

        // filter pt from network and make networks smaller for testing
        var net = NetworkUtils.readNetwork("/Users/janek/Documents/rust_q_sim/input/010.output_network.xml.gz");
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

        NetworkUtils.writeNetwork(cutNetwork(populationSingle, net), "/Users/janek/Documents/rust_q_sim/input/rvr-single.network.xml.gz");
        NetworkUtils.writeNetwork(cutNetwork(population01pct, net), "/Users/janek/Documents/rust_q_sim/input/rvr-01pct.network.xml.gz");
        NetworkUtils.writeNetwork(cutNetwork(population1pct, net), "/Users/janek/Documents/rust_q_sim/input/rvr-1pct.network.xml.gz");
        NetworkUtils.writeNetwork(net, "/Users/janek/Documents/rust_q_sim/input/rvr.network.xml.gz");
    }

    private static Network cutNetwork(Population pop, Network net) {
        var bbox = boundingBox(pop, net);
        var bboxNodes = new HashSet<Node>();
        ((SearchableNetwork)net).getNodeQuadTree().getRectangle(bbox, bboxNodes);
        return net.getLinks().values().parallelStream()
                .filter(link -> bboxNodes.contains(link.getFromNode()))
                .filter(link -> bboxNodes.contains(link.getToNode()))
                .collect(NetworkUtils.getCollector());
    }

    private static QuadTree.Rect boundingBox(Population pop, Network network) {

        var coordinats = pop.getPersons().values().stream()
                .flatMap(p -> getNodes(p.getSelectedPlan(), network))
                .toArray(Coordinate[]::new);

        var linearRing = new GeometryFactory().createLineString(coordinats);
        return new QuadTree.Rect(
                linearRing.getEnvelopeInternal().getMinX(),
                linearRing.getEnvelopeInternal().getMinY(),
                linearRing.getEnvelopeInternal().getMaxX(),
                linearRing.getEnvelopeInternal().getMaxY()
        );
    }

    private static Stream<Coordinate> getNodes(Plan plan, Network network) {

        return plan.getPlanElements().stream()
                .filter(e -> e instanceof Leg)
                .map(e -> (Leg) e)
                .flatMap(leg -> {
                    if (leg.getRoute() instanceof NetworkRoute nr) {
                        return nr.getLinkIds().stream();
                    } else if (leg.getRoute() instanceof GenericRouteImpl gr) {
                        return Stream.of(gr.getStartLinkId(), gr.getEndLinkId());
                    } else {
                        return Stream.empty();
                    }
                })
                .map(id -> network.getLinks().get(id))
                .flatMap(link -> Stream.of(link.getFromNode().getCoord(), link.getToNode().getCoord()))
                .map(MGC::coord2Coordinate);
    }
}
