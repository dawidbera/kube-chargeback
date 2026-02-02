package io.kubechargeback.collector;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;

@TopCommand
@Command(name = "chargeback-collector", mixinStandardHelpOptions = true)
public class ChargebackCollectorCommand implements Runnable {

    @Inject
    CollectorService collectorService;

    /**
     * Executes the chargeback collector command.
     */
    @Override
    public void run() {
        collectorService.runCollection();
    }
}
