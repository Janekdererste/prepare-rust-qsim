package org.matsim.prepare;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.algorithms.PersonAlgorithm;

public class PtFilter implements PersonAlgorithm {

    private final PersonAlgorithm next;
    private final Scenario scenario;

    public PtFilter(PersonAlgorithm next, Scenario scenario) {
        this.next = next;
        this.scenario = scenario;
    }


    @Override
    public void run(Person person) {
        if (UpscaleAlgorithm.isPtPerson(person)) return; // exclude all pt persons.

        UpscaleAlgorithm.removeExceptSelectedPlan(person);
        UpscaleAlgorithm.setActCoordsFromFacilities(person, scenario);

        if (next != null)
            next.run(person);
    }
}
