package org.matsim.prepare;

import org.matsim.api.core.v01.Id;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;

public class FilterPopulation {

    public static void main(String[] args) {

        var pop = PopulationUtils.readPopulation("/Users/janek/Documents/rust_q_sim/input/rvr.1pct.plans.xml.gz");
        var person = pop.getPersons().get(Id.createPersonId("1267938"));
        var outPop = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        outPop.addPerson(person);
        PopulationUtils.writePopulation(outPop, "/Users/janek/Documents/rust_q_sim/input/rvr.single-1267938.plans.xml.gz");
    }
}
