package org.matsim.prepare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.controler.Injector;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.algorithms.PersonAlgorithm;
import org.matsim.core.population.algorithms.XY2Links;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.PlanRouter;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UpscaleAlgorithm implements PersonAlgorithm {

    private static final Logger log = LogManager.getLogger(UpscaleAlgorithm.class);

    private final PlanRouter router;
    private final XY2Links xy2Links;
    private final Map<String, VehicleType> modeVehicleTypes;
    private final Scenario scenario;
    private final Config config;
    private final Collection<? extends PersonAlgorithm> personAlgorithms;
    private final double factor;
    private final List<Random> rnds = new ArrayList<>();

    public UpscaleAlgorithm(double factor, PlanRouter router, XY2Links xy2Links, Config config, Scenario scenario, Collection<? extends PersonAlgorithm> personAlgorithms) {
        this.router = router;
        this.xy2Links = xy2Links;
        this.modeVehicleTypes = createModeVehicleTypes(config, scenario);
        this.scenario = scenario;
        this.config = config;
        this.personAlgorithms = personAlgorithms;
        this.factor = factor;
        for (var i = 0; i < factor - 1; i++) {
            rnds.add(new Random(i));
        }
    }

    static UpscaleAlgorithm create(double factor, String eventsFile, Scenario scenario, Collection<? extends PersonAlgorithm> algorithms) {
        var injector = Injector.createMinimalMatsimInjector(scenario.getConfig(), scenario);
        var eventsManager = injector.getInstance(EventsManager.class);
        EventsUtils.readEvents(eventsManager, eventsFile);
        var tripRouter = injector.getInstance(TripRouter.class);
        var timeInterpretation = injector.getInstance(TimeInterpretation.class);
        var planRouter = new PlanRouter(tripRouter, timeInterpretation);
        var carOnlyNet = scenario.getNetwork().getLinks().values().stream()
                .filter(link -> link.getAllowedModes().contains(TransportMode.car))
                .collect(NetworkUtils.getCollector());
        var xy2Links = new XY2Links(carOnlyNet, scenario.getActivityFacilities());
        return new UpscaleAlgorithm(factor, planRouter, xy2Links, scenario.getConfig(), scenario, algorithms);
    }

    @Override
    public void run(Person person) {

        if (isPtPerson(person)) return; // exclude all pt persons.

        removeExceptSelectedPlan(person);
        setActCoordsFromFacilities(person);

        // try to process cloned agents in parallel.
        // it would be better to have this run method parallelized, but I think
        // that is much more work. So, we compromise on approach, which is cheap
        // to implement
        Stream.iterate(0, i -> i + 1).parallel()
                .limit((int) factor - 1)
                .map(i -> clonePerson(person, i))
                .forEach(cloned -> {
                    addModeVehicles(cloned, scenario);
                    addRoutingModeIfNecessary(cloned, config);
                    preparePersonForSim(cloned);

                    for (var algorithm : personAlgorithms) {
                        synchronized (algorithm) {
                            algorithm.run(cloned);
                        }
                    }
                });

        // we can call all algorithms in parallel for the last person
        personAlgorithms.parallelStream().forEach(algorithm -> {
            algorithm.run(person);
        });
    }

    private boolean isPtPerson(Person person) {
        var mode = TransportMode.pt;
        return person.getSelectedPlan().getPlanElements().stream()
                .filter(e -> e instanceof Leg)
                .map(e -> (Leg) e)
                .anyMatch(l -> l.getRoutingMode().contains(mode) || l.getMode().contains(mode));
    }

    private void removeExceptSelectedPlan(Person person) {
        var selecedPlan = person.getSelectedPlan();
        person.getPlans().clear();
        person.addPlan(selecedPlan);
    }

    private void setActCoordsFromFacilities(Person person) {
        var acts = TripStructureUtils.getActivities(person.getSelectedPlan(), TripStructureUtils.StageActivityHandling.ExcludeStageActivities);
        for (var a : acts) {
            if (a.getLinkId() == null) {
                var facility = scenario.getActivityFacilities().getFacilities().get(a.getFacilityId());

                System.out.println("Found Empty activity. Adding Coord: " + facility.getCoord() + " and link-id:" + facility.getLinkId() + " to activity");
                a.setCoord(facility.getCoord());
                a.setLinkId(facility.getLinkId());
            }
        }
    }

    private Person clonePerson(Person person, int i) {

        var trips = TripStructureUtils.getTrips(person.getSelectedPlan());
        var mainActs = TripStructureUtils.getActivities(person.getSelectedPlan(), TripStructureUtils.StageActivityHandling.ExcludeStageActivities);
        assertNumberOfActsAndTrips(mainActs, trips);
        var factory = scenario.getPopulation().getFactory();
        var rnd = rnds.get(i);

        var tripIter = trips.iterator();
        var actIter = mainActs.iterator();
        var newPerson = factory.createPerson(Id.createPersonId(person.getId().toString() + "_cloned_" + i));
        var newPlan = factory.createPlan();

        while (actIter.hasNext()) {
            var act = actIter.next();
            var rndCoord = createRandomCoord(act.getCoord(), rnd);
            var newAct = factory.createActivityFromCoord(act.getType(), rndCoord);
            if (act.getStartTime().isDefined()) {
                newAct.setStartTime(createRandomTime(act.getStartTime().seconds(), rnd));
            }
            if (act.getEndTime().isDefined()) {
                newAct.setEndTime(createRandomTime(act.getEndTime().seconds(), rnd));
            }
            if (act.getMaximumDuration().isDefined()) {
                newAct.setMaximumDuration(createRandomTime(act.getMaximumDuration().seconds(), rnd));
            }

            newPlan.addActivity(newAct);

            if (tripIter.hasNext()) {
                var trip = tripIter.next();
                var mainMode = TripStructureUtils.identifyMainMode(trip.getTripElements());
                var leg = factory.createLeg(mainMode);
                newPlan.addLeg(leg);
            }
        }

        newPerson.addPlan(newPlan);
        return newPerson;
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

    private static double createRandomTime(double time, Random rnd) {
        var rndTime = rnd.nextDouble(time - 1800, time + 1800);
        return Math.max(0, rndTime); // don't allow negative times.
    }

    private void preparePersonForSim(Person person) {

        var plan = person.getSelectedPlan();
        this.xy2Links.run(plan);
        this.router.run(plan);
    }

    private static Map<String, VehicleType> createModeVehicleTypes(Config config, Scenario scenario) {
        return Stream.concat(config.qsim().getMainModes().stream(), config.plansCalcRoute().getNetworkModes().stream())
                .distinct()
                .map(mode -> Tuple.of(mode, scenario.getVehicles().getVehicleTypes().get(Id.create(mode, VehicleType.class))))
                .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));
    }

    private void addModeVehicles(Person person, Scenario scenario) {

        var mode2Vehicle = new HashMap<String, Id<Vehicle>>();

        for (var entry : modeVehicleTypes.entrySet()) {
            var vehId = VehicleUtils.createVehicleId(person, entry.getKey());
            var veh = scenario.getVehicles().getFactory().createVehicle(vehId, entry.getValue());

            // add veh to scenario. Don't know whether we should delete it later?
            scenario.getVehicles().addVehicle(veh);
            mode2Vehicle.put(entry.getKey(), vehId);
        }

        VehicleUtils.insertVehicleIdsIntoAttributes(person, mode2Vehicle);
    }

    /// Stuff below is copied from personprepareforsim because it was not public.
    private void addRoutingModeIfNecessary(Person person, Config config) {

        var plan = person.getSelectedPlan();
        for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(plan.getPlanElements())) {
            List<Leg> legs = trip.getLegsOnly();
            if (!legs.isEmpty()) {
                String routingMode = TripStructureUtils.getRoutingMode(legs.get(0));

                for (Leg leg : legs) {
                    // 1. check all legs either have the same routing mode or all have routingMode==null
                    if (TripStructureUtils.getRoutingMode(leg) == null) {
                        if (routingMode != null) {
                            String errorMessage = "Found a mixed trip having some legs with routingMode set and others without. "
                                    + "This is inconsistent. Agent id: " + person.getId().toString()
                                    + "\nTrip: " + trip.getTripElements().toString();
                            log.error(errorMessage);
                            throw new RuntimeException(errorMessage);
                        }

                    } else {
                        if (routingMode.equals(TripStructureUtils.getRoutingMode(leg))) {
                            TripStructureUtils.setRoutingMode(leg, routingMode);
                        } else {
                            String errorMessage = "Found a trip whose legs have different routingModes. "
                                    + "This is inconsistent. Agent id: " + person.getId().toString()
                                    + "\nTrip: " + trip.getTripElements().toString();
                            log.error(errorMessage);
                            throw new RuntimeException(errorMessage);
                        }
                    }
                }

                // add routing mode
                if (routingMode == null) {
                    if (legs.size() == 1) {
                        // there is only a single leg (e.g. after Trips2Legs and a mode choice replanning
                        // module)

                        String oldMainMode = replaceOutdatedFallbackModesAndReturnOldMainMode(legs.get(0),
                                null);
                        if (oldMainMode != null) {
                            routingMode = oldMainMode;
                            TripStructureUtils.setRoutingMode(legs.get(0), routingMode);
                        } else {
                            // leg has a real mode (not an outdated fallback mode)
                            routingMode = legs.get(0).getMode();
                            TripStructureUtils.setRoutingMode(legs.get(0), routingMode);
                        }
                    } else {
                        if (config.plans().getHandlingOfPlansWithoutRoutingMode().equals(PlansConfigGroup.HandlingOfPlansWithoutRoutingMode.useMainModeIdentifier)) {
                            for (Leg leg : legs) {
                                replaceOutdatedAccessEgressWalkModes(leg, routingMode);
                            }
                            routingMode = getAndAddRoutingModeFromBackwardCompatibilityMainModeIdentifier(
                                    person, trip);
                        } else {
                            String errorMessage = "Found a trip with multiple legs and no routingMode. "
                                    + "Person id " + person.getId().toString()
                                    + "\nTrip: " + trip.getTripElements().toString()
                                    + "\nTerminating. Take care to inject an adequate MainModeIdentifier and set config switch "
                                    + "plansConfigGroup.setHandlingOfPlansWithoutRoutingMode("
                                    + PlansConfigGroup.HandlingOfPlansWithoutRoutingMode.useMainModeIdentifier.toString() + ").";
                            log.error(errorMessage);
                            throw new RuntimeException(errorMessage);
                        }
                    }
                }

                for (Leg leg : legs) {
                    // check before replaceOutdatedAccessEgressHelperModes
                    if (leg.getMode().equals(TransportMode.walk) && leg.getRoute() instanceof NetworkRoute) {
                        log.error(
                                "Found a walk leg with a NetworkRoute. This is the only allowed use case of having "
                                        + "non_network_walk as an access/egress mode. PrepareForSimImpl replaces "
                                        + "non_network_walk with walk, because access/egress to modes other than walk should "
                                        + "use the walk Router. If this causes any problem please report to gleich or kai -nov'19");
                    }
                }

                for (Leg leg : legs) {
                    replaceOutdatedAccessEgressWalkModes(leg, routingMode);
                    replaceOutdatedNonNetworkWalk(leg, routingMode);
                    replaceOutdatedFallbackModesAndReturnOldMainMode(leg, routingMode);
                }
            }
        }
    }

    private String getAndAddRoutingModeFromBackwardCompatibilityMainModeIdentifier(Person person, TripStructureUtils.Trip trip) {
        throw new RuntimeException("Not implemented");
    }

    private void replaceOutdatedAccessEgressWalkModes(Leg leg, String routingMode) {
        // access_walk and egress_walk were replaced by non_network_walk
        if (leg.getMode().equals("access_walk") || leg.getMode().equals("egress_walk")) {
            leg.setMode(TransportMode.non_network_walk);
            TripStructureUtils.setRoutingMode(leg, routingMode);
        }
    }

    // non_network_walk as access/egress to modes other than walk on the network was replaced by walk. -
    // kn/gl-nov'19
    private void replaceOutdatedNonNetworkWalk(Leg leg, String routingMode) {
        if (leg.getMode().equals(TransportMode.non_network_walk)) {
            leg.setMode(TransportMode.walk);
            TripStructureUtils.setRoutingMode(leg, routingMode);
        }
    }

    private String replaceOutdatedFallbackModesAndReturnOldMainMode(Leg leg, String routingMode) {
        // transit_walk was replaced by walk (formerly fallback and access/egress/transfer to pt mode)
        if (leg.getMode().equals(TransportMode.transit_walk)) {
            leg.setMode(TransportMode.walk);
            TripStructureUtils.setRoutingMode(leg, routingMode);
            return TransportMode.pt;
        }

        // replace drt_walk etc. (formerly fallback and access/egress to drt modes)
        if (leg.getMode().endsWith("_walk") && !leg.getMode().equals(TransportMode.non_network_walk)) {
            String oldMainMode = leg.getMode().substring(0, leg.getMode().length() - 5);
            leg.setMode(TransportMode.walk);
            TripStructureUtils.setRoutingMode(leg, routingMode);
            return oldMainMode;
        }

        // replace drt_fallback etc. (formerly fallback for drt modes)
        if (leg.getMode().endsWith("_fallback")) {
            String oldMainMode = leg.getMode().substring(0, leg.getMode().length() - 9);
            leg.setMode(TransportMode.walk);
            TripStructureUtils.setRoutingMode(leg, routingMode);
            return oldMainMode;
        }

        return null;
    }
}
