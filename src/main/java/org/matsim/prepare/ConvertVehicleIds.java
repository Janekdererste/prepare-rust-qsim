package org.matsim.prepare;


import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.NetworkRoute;

public class ConvertVehicleIds {

    public static void main(String[] args) {

        var pop = PopulationUtils.readPopulation("/Users/janek/projects/rust_q_sim/assets/equil/equil-plans.xml");

        pop.getPersons().values().stream()
                .flatMap(person -> person.getPlans().stream())
                .flatMap(plan -> plan.getPlanElements().stream())
                .filter(planElement -> planElement instanceof Leg)
                .map(planElement -> (Leg)planElement)
                .map(Leg::getRoute)
                .filter(route -> route instanceof NetworkRoute)
                .forEach(route -> {
                    var id = ((NetworkRoute) route).getVehicleId();
                    var newId = Id.createVehicleId(id.toString() + "_car");
                    ((NetworkRoute) route).setVehicleId(newId);
                });

        PopulationUtils.writePopulation(pop, "/Users/janek/projects/rust_q_sim/assets/equil/equil-plans.xml.gz");
    }
}
