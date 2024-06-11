package org.matsim.prepare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.algorithms.PersonAlgorithm;

public class LogAlgorithm implements PersonAlgorithm {
    private static final Logger log = LogManager.getLogger(LogAlgorithm.class);

    @Override
    public void run(Person person) {
        log.info(person.getId());
    }
}
