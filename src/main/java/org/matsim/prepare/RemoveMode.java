package org.matsim.prepare;

import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;

public class RemoveMode {

    public static void remove(Scenario scenario, String mode) {

        var it = scenario.getPopulation().getPersons().values().iterator();
        while (it.hasNext()) {
            var person = it.next();
            var matched = person.getSelectedPlan().getPlanElements().stream()
                    .filter(e -> e instanceof Leg)
                    .map(e -> (Leg) e)
                    .anyMatch(l -> l.getMode().equals(mode));
            if (matched) it.remove();
        }

        // filter pt from network and make networks smaller for testing
        var net = scenario.getNetwork();
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
    }
}
