package org.matsim.prepare;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.Injector;
import org.matsim.core.controler.PrepareForSim;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.router.TripStructureUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;

public class UpscalePopulation {

    private static final Random rnd = new Random(42);

    public static void upscalePopulation(Scenario scenario, double factor) {
        for (var person : new HashSet<>(scenario.getPopulation().getPersons().values())) {
            var cloned = clonePerson(person, factor, scenario.getPopulation().getFactory());
            for (var clonedPerson : cloned) {
                scenario.getPopulation().addPerson(clonedPerson);
            }
        }
    }

    public static void prepareForSim(Scenario scenario, String events) {

        var injector = Injector.createMinimalMatsimInjector(scenario.getConfig(), scenario);

        // read in events
        EventsUtils.readEvents(injector.getInstance(EventsManager.class), events);
        var prepareForSim = injector.getInstance(PrepareForSim.class);
        prepareForSim.run();
    }

    private static Collection<Person> clonePerson(Person person, double factor, PopulationFactory factory) {

        var trips = TripStructureUtils.getTrips(person.getSelectedPlan());
        var mainActs = TripStructureUtils.getActivities(person.getSelectedPlan(), TripStructureUtils.StageActivityHandling.ExcludeStageActivities);
        assertNumberOfActsAndTrips(mainActs, trips);
        var result = new ArrayList<Person>(person.getSelectedPlan().getPlanElements().size());

        // use < factor -1 because we have one person already. If we want to scale 10x, we need to add 9 persons
        for (var i = 0; i < factor - 1; i++) {
            var tripIter = trips.iterator();
            var actIter = mainActs.iterator();
            var newPerson = factory.createPerson(Id.createPersonId(person.getId().toString() + "_cloned_" + i));
            var newPlan = factory.createPlan();

            while (actIter.hasNext()) {
                var act = actIter.next();
                var rndCoord = createRandomCoord(act.getCoord(), rnd);
                var newAct = factory.createActivityFromCoord(act.getType(), new Coord(act.getCoord().getX(), act.getCoord().getY()));
                if (act.getStartTime().isDefined()) {
                    newAct.setStartTime(act.getStartTime().seconds());
                }
                if (act.getEndTime().isDefined()) {
                    newAct.setEndTime(act.getEndTime().seconds());
                }
                if (act.getMaximumDuration().isDefined()) {
                    newAct.setMaximumDuration(act.getMaximumDuration().seconds());
                }

                newPlan.addActivity(act);

                if (tripIter.hasNext()) {
                    var trip = tripIter.next();
                    var mainMode = TripStructureUtils.identifyMainMode(trip.getTripElements());
                    var leg = factory.createLeg(mainMode);
                    newPlan.addLeg(leg);
                }
            }

            newPerson.addPlan(newPlan);
            result.add(newPerson);
        }
        return result;
    }

    private static void assertNumberOfActsAndTrips(Collection<Activity> act, Collection<TripStructureUtils.Trip> trips) {
        if (act.size() != trips.size() + 1) {
            throw new RuntimeException("Assuming that we always have at one more activity than trip. Because plans look like: \nActivity->Leg->Activity->Leg->Activiy");
        }
    }

    private static Coord createRandomCoord(Coord coord, Random rnd) {

        var x = rnd.nextDouble(coord.getX() - 100, coord.getX() + 100);
        var y = rnd.nextDouble(coord.getY() - 100, coord.getY() + 100);
        return new Coord(x, y);
    }
}
