package org.matsim.prepare;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.router.TripStructureUtils;

public class CleanPopulation {

    public static void clean(Scenario scenario) {

        for (var person : scenario.getPopulation().getPersons().values()) {
            var acts = TripStructureUtils.getActivities(person.getSelectedPlan(), TripStructureUtils.StageActivityHandling.ExcludeStageActivities);
            for (var a : acts) {
                if (a.getLinkId() == null) {
                    var facility = scenario.getActivityFacilities().getFacilities().get(a.getFacilityId());

                    System.out.println("Found Empty activity. Adding Coord: " + facility.getCoord() + " and link-id:" + facility.getLinkId() + " to activity");
                    a.setCoord(facility.getCoord());
                    a.setLinkId(facility.getLinkId());
                }
            }
            var selecedPlan = person.getSelectedPlan();
            person.getPlans().clear();
            person.addPlan(selecedPlan);
        }
    }
}
