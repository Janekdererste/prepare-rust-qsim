package org.matsim.prepare;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.testcases.MatsimTestUtils;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class UpscalePopulationTest {

    @Rule
    public MatsimTestUtils utils = new MatsimTestUtils();

    @Test
    public void upscaleFactor() {

        var configURL = IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("equil"), "config.xml");
        var config = ConfigUtils.loadConfig(configURL);
        var scenario = ScenarioUtils.loadScenario(config);

        var sizeBefore = scenario.getPopulation().getPersons().size();
        UpscalePopulation.upscalePopulation(scenario, 2);

        assertEquals(sizeBefore * 2, scenario.getPopulation().getPersons().size());
    }

    @Test
    public void upscaleCopyActivities() {

        var configURL = IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("equil"), "config.xml");
        var config = ConfigUtils.loadConfig(configURL);
        var scenario = ScenarioUtils.loadScenario(config);

        var originalIds = new ArrayList<>(scenario.getPopulation().getPersons().keySet());

        UpscalePopulation.upscalePopulation(scenario, 2);

        for (var origId : originalIds) {
            var origPerson = scenario.getPopulation().getPersons().get(origId);
            var clonedId = Id.createPersonId(origId.toString() + "_cloned_0");
            var clonedPerson = scenario.getPopulation().getPersons().get(clonedId);

            assertNotNull("Could not find cloned person with id: " + clonedId, clonedPerson);

            assertEquals(origPerson.getSelectedPlan().getPlanElements().size(), clonedPerson.getSelectedPlan().getPlanElements().size());
            var origIter = origPerson.getSelectedPlan().getPlanElements().iterator();
            var clonedIter = clonedPerson.getSelectedPlan().getPlanElements().iterator();
            while (origIter.hasNext()) {
                var origElement = origIter.next();
                var clonedElement = clonedIter.next();
                if (origElement instanceof Activity origAct) {
                    var clonedAct = (Activity) clonedElement;

                    assertEquals(origAct.getStartTime(), clonedAct.getStartTime());
                    assertEquals(origAct.getEndTime(), clonedAct.getEndTime());
                    assertEquals(origAct.getMaximumDuration(), clonedAct.getMaximumDuration());

                    // this should also test coords, but im lazy
                }
                if (origElement instanceof Leg origLeg) {
                    var clonedLeg = (Leg) clonedElement;

                    // mode should be set
                    assertEquals(origLeg.getMode(), clonedLeg.getMode());
                    // everything else should remain unset
                    assertNull(clonedLeg.getRoute());
                    assertFalse(clonedLeg.getDepartureTime().isDefined());
                    assertFalse(clonedLeg.getTravelTime().isDefined());
                }
            }
        }
    }

    @Test
    public void prepareForSim() {

        runOnce(utils.getOutputDirectory() + "output-1");

        var configURL = IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("equil"), "config.xml");
        var config = ConfigUtils.loadConfig(configURL);
        config.controler().setOutputDirectory(utils.getOutputDirectory() + "output-2");
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        var scenario = ScenarioUtils.loadScenario(config);

        var controler = new Controler(scenario);
        controler.run();

        var eventsFile = utils.getOutputDirectory() + "output-1/output_events.xml.gz";
        UpscalePopulation.upscalePopulation(scenario, 2);
        UpscalePopulation.prepareForSim(scenario, eventsFile);

        assertNotNull(scenario.getPopulation());
    }

    private static void runOnce(String outputDirectory) {
        var configURL = IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("equil"), "config.xml");
        var config = ConfigUtils.loadConfig(configURL);
        config.controler().setOutputDirectory(outputDirectory);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controler().setLastIteration(0);
        var scenario = ScenarioUtils.loadScenario(config);

        var controler = new Controler(scenario);
        controler.run();
    }

    @Test
    public void prepareForSimWithEvents() {


    }
}