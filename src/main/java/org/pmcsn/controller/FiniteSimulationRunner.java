package org.pmcsn.controller;

import org.pmcsn.centers.SimpleCenter;
import org.pmcsn.configuration.CenterFactory;
import org.pmcsn.configuration.ConfigurationManager;
import org.pmcsn.libraries.Rngs;
import org.pmcsn.model.*;
import org.pmcsn.utils.*;

import java.util.ArrayList;
import java.util.List;

import static org.pmcsn.model.EventType.*;
import static org.pmcsn.utils.AnalyticalComputation.computeAnalyticalResults;
import static org.pmcsn.utils.Comparison.compareResults;
import static org.pmcsn.utils.PrintUtils.printFinalResults;
import static org.pmcsn.utils.Verification.verifyConfidenceIntervals;

public class FiniteSimulationRunner {
    private static final ConfigurationManager config = new ConfigurationManager();
    private final int start = 0;
    private final double stop = config.getDouble("general", "finiteSimObservationTime"); // 8 hours
    private final long seed;
    private final int rngStreamIndex = config.getInt("general", "seedStreamIndex");
    private final int runsNumber = config.getInt("general", "runsNumber");

    private SimpleCenter smallCenter;
    private SimpleCenter mediumCenter;
    private SimpleCenter largeCenter;

    private List<Observations> smallCenterObservation;
    private List<Observations> mediumCenterObservation;
    private List<Observations> largeCenterObservation;


    public FiniteSimulationRunner() {
        this(123456789L);
    }

    public FiniteSimulationRunner(long seed) {
        this.seed = seed;
    }

    public void runFiniteSimulation(
            boolean approximateServiceAsExponential,
            boolean shouldTrackObservations) throws Exception {
        initCenters(approximateServiceAsExponential);

        System.out.println("[DEBUG] runFiniteSimulation: trackObs=" + shouldTrackObservations);
        String simulationType = getSimulationType(approximateServiceAsExponential);
        System.out.println("\nRUNNING " + simulationType + "...");

        //Rng setting the seed
        long[] seeds = new long[1024];
        seeds[0] = seed;
        Rngs rngs = new Rngs();

        String observationsPath = "csvFiles/%s/%d/observations".formatted(simulationType, seed);
        if (shouldTrackObservations) {
            initObservations(observationsPath);
        }

        for (int i = 0; i < runsNumber; i++) {
            long number = 1;

            rngs.plantSeeds(seeds[i]);

            //Msq initialization
            MsqTime msqTime = new MsqTime();
            msqTime.current = start;
            EventQueue queue;
            if (shouldTrackObservations) {
                queue = new FiniteSimulationEventQueue();
            } else {
                queue = new EventQueue();
            }

           resetCenters(rngs, queue);

            MsqEvent event;
            // need to use OR because all the conditions should be false
            while (!smallCenter.isEndOfArrivals() || !mediumCenter.isEndOfArrivals() || !largeCenter.isEndOfArrivals() || !queue.isEmpty() || number != 0) {
                // Retrieving next event to be processed
                event = queue.pop();
                if (event.type == EventType.SAVE_STAT) {
                    System.out.println("[DEBUG] SAVE_STAT at t=" + event.time);
                    System.out.println("  smallObs size=" + smallCenterObservation.size());
                    System.out.println("  medObs size="   + mediumCenterObservation.size());
                    System.out.println("  largObs size="  + largeCenterObservation.size());
                    smallCenter.updateObservations(smallCenterObservation);
                    mediumCenter.updateObservations(mediumCenterObservation);
                    largeCenter.updateObservations(largeCenterObservation);
                    continue;
                }
                msqTime.next = event.time;

                // Updating areas
                updateAreas(msqTime);

                // Advancing the clock
                msqTime.current = msqTime.next;

                // Processing the event based on its type
                processCurrentEvent(event, msqTime, queue);

                number = getTotalNumberOfJobsInSystem();
            }

            // Writing observations for current run
            if (shouldTrackObservations) {
                writeObservations(observationsPath);
                resetObservations();
            }

            // Saving statistics for current run
            saveAllStats();

            // Generating next seed
            rngs.selectStream(rngStreamIndex);
            seeds[i + 1] = rngs.getSeed();
        }

        System.out.println(simulationType + " HAS JUST FINISHED");

        if (shouldTrackObservations) {
            PlotUtils.welchPlot(observationsPath);
        }

        // Writing statistics csv with data from all runs
        writeAllStats(simulationType, seed);

        if (approximateServiceAsExponential) {
            modelVerification(simulationType); // Computing and writing verifications stats csv
        }
        System.out.println();
        printMeanResponseTime();
    }

    private String getSimulationType(boolean approximateServiceAsExponential) {
        String s;
        if (approximateServiceAsExponential) {
            s = "FINITE_SIMULATION_EXPONENTIAL";
        } else {
            s = "FINITE_SIMULATION";
        }
        return s;
    }

    private void initCenters(boolean approximateServiceAsExponential) {
        CenterFactory factory = new CenterFactory(false);
        smallCenter = factory.createSmallCenter(approximateServiceAsExponential, false);
        mediumCenter = factory.createMediumCenter(approximateServiceAsExponential, false);
        largeCenter = factory.createLargeCenter(approximateServiceAsExponential, false);
    }

    private void resetCenters(Rngs rngs, EventQueue queue) {
        // Initialize small
        smallCenter.start(rngs, start);
        smallCenter.setStop(stop);

        //generating first arrival
        double time = smallCenter.getArrival();
        MsqEvent firstEvent = new MsqEvent(ARRIVAL_SMALL_CENTER, time);
        queue.add(firstEvent);

        // Initialize medium
        mediumCenter.start(rngs, start);
        mediumCenter.setStop(stop);

        //generating first arrival
        time = mediumCenter.getArrival();
        firstEvent = new MsqEvent(ARRIVAL_MEDIUM_CENTER, time);
        queue.add(firstEvent);

        // Initialize large
        largeCenter.start(rngs, start);
        largeCenter.setStop(stop);

        //generating first arrival
        time = largeCenter.getArrival();
        firstEvent = new MsqEvent(EventType.ARRIVAL_LARGE_CENTER, time);
        queue.add(firstEvent);
    }

    private void processCurrentEvent(MsqEvent event, MsqTime msqTime, EventQueue events) {
        switch (event.type) {
            case ARRIVAL_SMALL_CENTER:
                smallCenter.processArrival(event, msqTime, events);
                smallCenter.generateNextArrival(events, ARRIVAL_SMALL_CENTER);
                break;
            case COMPLETION_SMALL_CENTER:
                smallCenter.processCompletion(event, msqTime, events);
                break;
            case ARRIVAL_MEDIUM_CENTER:
                mediumCenter.processArrival(event, msqTime, events);
                mediumCenter.generateNextArrival(events, ARRIVAL_MEDIUM_CENTER);
                break;
            case COMPLETION_MEDIUM_CENTER:
                mediumCenter.processCompletion(event, msqTime, events);
                break;
            case ARRIVAL_LARGE_CENTER:
                largeCenter.processArrival(event, msqTime, events);
                largeCenter.generateNextArrival(events, ARRIVAL_LARGE_CENTER);
                break;
            case COMPLETION_LARGE_CENTER:
                largeCenter.processCompletion(event, msqTime, events);
                break;
        }
    }

    private void saveAllStats() {
        smallCenter.saveStats();
        mediumCenter.saveStats();
        largeCenter.saveStats();
    }

    private void modelVerification(String simulationType) {
        List<AnalyticalComputation.AnalyticalResult> analyticalResultList = computeAnalyticalResults(simulationType);

        // Compare results and verifications and save comparison result
        List<MeanStatistics> meanStatisticsList = aggregateMeanStatistics();

        List<Comparison.ComparisonResult> comparisonResultList = compareResults(simulationType, analyticalResultList, meanStatisticsList);

        List<ConfidenceIntervals> confidenceIntervalsList = aggregateConfidenceIntervals();

        List<Verification.VerificationResult> verificationResultList = verifyConfidenceIntervals(simulationType, meanStatisticsList, comparisonResultList, confidenceIntervalsList);

        printFinalResults(verificationResultList, 0, 0);
    }

    private List<MeanStatistics> aggregateMeanStatistics() {
        List<MeanStatistics> meanStatisticsList = new ArrayList<>();

        meanStatisticsList.add(smallCenter.getMeanStatistics());
        meanStatisticsList.add(mediumCenter.getMeanStatistics());
        meanStatisticsList.add(largeCenter.getMeanStatistics());
        return meanStatisticsList;
    }

    private void printMeanResponseTime(){
        System.out.println("Average response time in REPARTO ISTRUTTORIE: "+ smallCenter.getMeanStatistics().meanResponseTime);
        System.out.println("Average response time in SISTEMA SCORING AUTOMATICO: "+ mediumCenter.getMeanStatistics().meanResponseTime);
        System.out.println("Average response time in COMITATO CREDITO: " + largeCenter.getMeanStatistics().meanResponseTime);
    }

    private List<ConfidenceIntervals> aggregateConfidenceIntervals() {
        List<ConfidenceIntervals> confidenceIntervalsList = new ArrayList<>();
        confidenceIntervalsList.add(createConfidenceIntervals(smallCenter.getStatistics()));
        confidenceIntervalsList.add(createConfidenceIntervals(mediumCenter.getStatistics()));
        confidenceIntervalsList.add(createConfidenceIntervals(largeCenter.getStatistics()));
        return confidenceIntervalsList;
    }

    private ConfidenceIntervals createConfidenceIntervals(BasicStatistics stats) {
        return new ConfidenceIntervals(
                stats.meanResponseTimeList, stats.meanQueueTimeList, stats.meanServiceTimeList,
                stats.meanSystemPopulationList, stats.meanQueuePopulationList, stats.meanUtilizationList, stats.lambdaList
        );
    }

    private void writeAllStats(String simulationType, long seed) {
        System.out.println("Writing csv files with stats for all the centers.");
        smallCenter.writeStats(simulationType, seed);
        mediumCenter.writeStats(simulationType, seed);
        largeCenter.writeStats(simulationType, seed);
    }

    private long getTotalNumberOfJobsInSystem() {
        return smallCenter.getNumberOfJobsInNode() +
                mediumCenter.getNumberOfJobsInNode() +
                largeCenter.getNumberOfJobsInNode();
    }

    private void updateAreas(MsqTime msqTime) {
        // Updating the areas
        smallCenter.setArea(msqTime);
        mediumCenter.setArea(msqTime);
        largeCenter.setArea(msqTime);
    }

    private void initObservations(String path) {
        FileUtils.deleteDirectory(path);
        System.out.println("[DEBUG] initObservations path=" + path);

        System.out.println("[DEBUG] smallCenter servers=" + smallCenter.getServersNumber());
        smallCenterObservation = new ArrayList<>();
        for (int i = 0; i < smallCenter.getServersNumber(); i++) {
            smallCenterObservation.add(new Observations("%s_%d".formatted(smallCenter.getCenterName(), i + 1)));
        }

        System.out.println("[DEBUG] mediumCenter servers=" + mediumCenter.getServersNumber());
        mediumCenterObservation = new ArrayList<>();
        for (int i = 0; i < mediumCenter.getServersNumber(); i++) {
            mediumCenterObservation.add(new Observations("%s_%d".formatted(mediumCenter.getCenterName(), i + 1)));
        }

        System.out.println("[DEBUG] largeCenter servers=" + largeCenter.getServersNumber());
        largeCenterObservation = new ArrayList<>();
        for (int i = 0; i < largeCenter.getServersNumber(); i++) {
            largeCenterObservation.add(new Observations("%s_%d".formatted(largeCenter.getCenterName(), i + 1)));
        }
    }

    private void resetObservations() {
        smallCenterObservation.forEach(Observations::reset);
        mediumCenterObservation.forEach(Observations::reset);
        largeCenterObservation.forEach(Observations::reset);
    }

    private void writeObservations(String path) {
        PlotUtils.writeObservations(path, smallCenterObservation);
        PlotUtils.writeObservations(path, mediumCenterObservation);
        PlotUtils.writeObservations(path, largeCenterObservation);
    }
}
