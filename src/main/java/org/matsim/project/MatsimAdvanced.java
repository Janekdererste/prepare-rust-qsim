package org.matsim.project;

import com.google.inject.Inject;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.examples.ExamplesUtils;

import java.util.Map;
import java.util.Set;

public class MatsimAdvanced {

    public static void main(String[] args) {

        var baseUrl = ExamplesUtils.getTestScenarioURL("equil");
        var url = IOUtils.extendUrl(baseUrl, "config.xml");

        var config = ConfigUtils.loadConfig(url);
        config.controler().setLastIteration(0);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);

        var scenario = ScenarioUtils.loadScenario(config);

        var controler = new Controler(scenario);

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                this.addEventHandlerBinding().to(MyEventHandler.class);
                this.bind(MyHelper.class).to(MyHelperImpl1.class);

                var setBinder = Multibinder.newSetBinder(this.binder(), MyHelper.class);
                setBinder.addBinding().to(MyHelperImpl1.class);

                var mapBinder = MapBinder.newMapBinder(this.binder(), String.class, MyHelper.class);
                mapBinder.addBinding("one").to(MyHelperImpl1.class);
            }
        });

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                var setBinder = Multibinder.newSetBinder(this.binder(), MyHelper.class);
                setBinder.addBinding().to(MyHelperImpl2.class);

                var mapBinder = MapBinder.newMapBinder(this.binder(), String.class, MyHelper.class);
                mapBinder.addBinding("two").to(MyHelperImpl2.class);
            }
        });
        controler.run();
    }

    private static class MyEventHandler implements LinkLeaveEventHandler {

        @Inject
        MyHelper helper;

        @Inject
        Set<MyHelper> helpersCollection;

        @Inject
        Map<String, MyHelper> helpersMap;

        @Override
        public void handleEvent(LinkLeaveEvent event) {
            System.out.println(event.toString());
            helpersMap.get("two").help();
        }
    }

    interface MyHelper {
        void help();
    }

    private static class MyHelperImpl1 implements MyHelper {
        @Override
        public void help() {
            System.out.println("I have helped!!!");
        }
    }

    private static class MyHelperImpl2 implements MyHelper {

        @Override
        public void help() {
            System.out.println("I have helped 2!!!");
        }
    }
}
