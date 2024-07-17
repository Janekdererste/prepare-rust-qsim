package org.matsim.prepare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Injector;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.algorithms.PersonAlgorithm;
import org.matsim.core.population.algorithms.XY2Links;
import org.matsim.core.router.PlanRouter;
import org.matsim.core.router.TripRouter;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.vehicles.VehicleType;

import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

public class SimpleUpscaleAlgorithm implements PersonAlgorithm {

    private static final Logger log = LogManager.getLogger(UpscaleAlgorithm.class);

    private final PlanRouter router;
    private final XY2Links xy2Links;
    private final Map<String, VehicleType> modeVehicleTypes;
    private final Scenario scenario;
    private final Config config;
    private final PersonAlgorithm personAlgorithm;
    private final double factor;
    private final Random rnd = new Random(42);

    public SimpleUpscaleAlgorithm(double factor, PlanRouter router, XY2Links xy2Links, Config config, Scenario scenario, PersonAlgorithm personAlgorithm) {
        this.router = router;
        this.xy2Links = xy2Links;
        this.modeVehicleTypes = UpscaleAlgorithm.createModeVehicleTypes(config, scenario);
        this.scenario = scenario;
        this.config = config;
        this.personAlgorithm = personAlgorithm;
        this.factor = factor;
    }

    public static SimpleUpscaleAlgorithm create(double factor, String eventsFile, Scenario scenario, PersonAlgorithm algorithm) {
        var injector = Injector.createMinimalMatsimInjector(scenario.getConfig(), scenario);
        var eventsManager = injector.getInstance(EventsManager.class);
        // EventsUtils.readEvents(eventsManager, eventsFile);
        var tripRouter = injector.getInstance(TripRouter.class);
        var timeInterpretation = injector.getInstance(TimeInterpretation.class);
        var planRouter = new PlanRouter(tripRouter, timeInterpretation);
        var carOnlyNet = scenario.getNetwork().getLinks().values().stream()
                .filter(link -> link.getAllowedModes().contains(TransportMode.car))
                .collect(NetworkUtils.getCollector());
        var xy2Links = new XY2Links(carOnlyNet, scenario.getActivityFacilities());
        return new SimpleUpscaleAlgorithm(factor, planRouter, xy2Links, scenario.getConfig(), scenario, algorithm);
    }

    @Override
    public void run(Person person) {

        // clone person and write them to file
        if (factor > 1.0) upscale(person);

        // also write the original person into the file
        personAlgorithm.run(person);
    }

    private void upscale(Person person) {
        // clone agents
        Stream.iterate(0, i -> i + 1)
                .limit(calcLimit(factor, rnd))
                .map(i -> UpscaleAlgorithm.clonePerson(person, i, scenario, rnd))
                .forEach(cloned -> {
                    UpscaleAlgorithm.addModeVehicles(cloned, scenario, modeVehicleTypes);
                    UpscaleAlgorithm.addRoutingModeIfNecessary(person, config);
                    UpscaleAlgorithm.preparePersonForSim(cloned, xy2Links, router);
                    personAlgorithm.run(cloned); // write to file
                });
    }

    private static int calcLimit(double factor, Random rnd) {

        int floor = (int) factor;
        double diff = factor - floor;
        int add = rnd.nextDouble() <= diff ? 1 : 0;
        // subtract 1, as we are taking the original agent as well in any case. for example:
        // for factor = 2.0 we must clone original agent once, because we will include the original
        // agent and the cloned one.
        return floor - 1 + add;
    }
}
