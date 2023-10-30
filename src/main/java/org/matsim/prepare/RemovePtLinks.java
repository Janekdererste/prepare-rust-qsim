package org.matsim.prepare;

import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.accessibility.utils.NetworkUtil;
import org.matsim.core.network.NetworkUtils;

import java.util.stream.Collectors;

public class RemovePtLinks {
    public static void main(String[] args) {
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

        NetworkUtils.writeNetwork(net, "/Users/janek/Documents/rust_q_sim/input/rvr-no-pt.network.xml.gz");
    }
}
