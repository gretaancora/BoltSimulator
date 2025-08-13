package org.pmcsn.controller;

import org.pmcsn.centers.*;
import org.pmcsn.configuration.CenterFactory;
import org.pmcsn.libraries.Rngs;
import org.pmcsn.model.*;
import org.pmcsn.utils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.pmcsn.model.EventType.*;
import static org.pmcsn.utils.AnalyticalComputation.computeAnalyticalResultsImproved;
import static org.pmcsn.utils.Comparison.compareResults;
import static org.pmcsn.utils.PrintUtils.*;
import static org.pmcsn.utils.PrintUtils.printDebug;
import static org.pmcsn.utils.Verification.verifyConfidenceIntervals;

public class BatchImprovedSimulationRunner {

    // Constants
    private static final int START = 0;
    private final long seed;

    // Centri
    private SimpleCenter largeCenter;
    private SimpleCenter mediumCenter;
    private SimpleCenter smallCenter;
    private RideCenter rideCenter;

    private List<Observations> smallCenterObservation;
    private List<Observations> mediumCenterObservation;
    private List<Observations> largeCenterObservation;
    private List<Observations> rideCenterObservation;

    // We need to compute autocorrelation on the series
    // Number of jobs in single batch (B)
    private final int batchSize;
    // Number of batches (K >= 40)
    private final int numBatches;
    private final int warmupThreshold;
    private boolean isWarmingUp = true;
    private final int intervalLength;
    EventQueue events = null;

    public BatchImprovedSimulationRunner(int batchSize, int numBatches, int warmupThreshold) {
        this(batchSize, numBatches, warmupThreshold, 123456789L, 20);
    }

    public BatchImprovedSimulationRunner(int batchSize, int numBatches, int warmupThreshold, long seed, int intervalLength) {
        this.batchSize = batchSize;
        this.numBatches = numBatches;
        this.warmupThreshold = warmupThreshold;
        this.seed = seed;
        this.intervalLength = intervalLength;
    }

    public void runBatchSimulation(boolean approximateServiceAsExponential) throws Exception {
        initCenters(approximateServiceAsExponential);

        String simulationType = getSimulationType(approximateServiceAsExponential);
        printDebug("\nRUNNING " + simulationType + "...");

        // Rng setting the seed
        Rngs rngs = new Rngs();
        rngs.plantSeeds(seed);

        String observationsPath = "csvFiles/%s/%d/observations".formatted(simulationType, seed);
        initObservations(observationsPath);

        // Initialize MsqTime
        MsqTime msqTime = new MsqTime();
        msqTime.current = START;
        events = new FiniteSimulationEventQueue(intervalLength);

        resetCenters(rngs, events);

        boolean stopWarmup = false;

        // the terminating condition is that all the centers have processed all the jobs
        while(!isDone()) {
            System.out.println("[DEBUG] Ciclo loop principale: isDone() = " + isDone());
            // Retrieving next event to be processed
            MsqEvent event = events.pop();
            System.out.println("[DEBUG] Evento processato: " + event);
            if (event.type == EventType.SAVE_STAT) {
                if (!isWarmingUp) {
                    smallCenter.updateObservations(smallCenterObservation);
                    mediumCenter.updateObservations(mediumCenterObservation);
                    largeCenter.updateObservations(largeCenterObservation);
                    rideCenter.updateObservations(rideCenterObservation);

                    System.out.printf("[DEBUG] Batches raccolti: small=%d, medium=%d, large=%d, ride=%d%n",
                            smallCenterObservation.size(),
                            mediumCenterObservation.size(),
                            largeCenterObservation.size(),
                            rideCenterObservation.size()
                    );
                }
                continue;
            }
            msqTime.next = event.time;

            // Updating areas
            updateAreas(msqTime);

            // Advancing the clock
            MsqTime currentTime = new MsqTime();
            currentTime.current = msqTime.current;
            // Advancing the clock
            msqTime.current = msqTime.next;

            if (stopWarmup) {
                stopWarmup(currentTime);
                stopWarmup = false;
            }

            // Processing the event based on its type
            processCurrentEvent(event, msqTime, events);


            // Checking if still in warmup period
            if (isWarmingUp && getMinimumNumberOfJobsServedByCenters() >= warmupThreshold ) {
                printSuccess("WARMUP COMPLETED... Starting to collect statistics for centers from now on.");
                isWarmingUp = false;
                stopWarmup = true;
            }
        }

        // The batch simulation has now ended. Time to collect the statistics
        printSuccess(simulationType + " HAS JUST FINISHED.");
        printDebug("Events queue size is " + events.size());

        writeObservations(observationsPath);
        PlotUtils.welchPlot(observationsPath);

        // Writing statistics csv with data from all batches
        writeAllStats(simulationType, seed);

        // Computing and writing verifications stats csv
        if (approximateServiceAsExponential) {
            modelVerification(simulationType);
        }

        getBatchStatistics();
    }

    private void initObservations(String path) {
        FileUtils.deleteDirectory(path);
        smallCenterObservation = new ArrayList<>();
        for (int i = 0; i < smallCenter.getServersNumber(); i++) {
            smallCenterObservation.add(new Observations("%s_%d".formatted(smallCenter.getCenterName(), i + 1)));
        }
        mediumCenterObservation = new ArrayList<>();
        for (int i = 0; i < mediumCenter.getServersNumber(); i++) {
            mediumCenterObservation.add(new Observations("%s_%d".formatted(mediumCenter.getCenterName(), i + 1)));
        }
        largeCenterObservation = new ArrayList<>();
        for (int i = 0; i < largeCenter.getServersNumber(); i++) {
            largeCenterObservation.add(new Observations("%s_%d".formatted(largeCenter.getCenterName(), i + 1)));
        }
        rideCenterObservation = new ArrayList<>();
        for (int i = 0; i < rideCenter.getServersNumber(); i++) {
            rideCenterObservation.add(new Observations("%s_%d".formatted(rideCenter.getCenterName(), i + 1)));
        }
    }

    private String getSimulationType(boolean approximateServiceAsExponential) {
        String s;
        if (approximateServiceAsExponential) {
            s = "IMPROVED_BATCH_SIMULATION_EXPONENTIAL";
        } else {
            s = "IMPROVED_BATCH_SIMULATION";
        }
        return s;
    }

    private void stopWarmup(MsqTime time) {
        smallCenter.stopWarmup(time);
        mediumCenter.stopWarmup(time);
        largeCenter.stopWarmup(time);
        rideCenter.stopWarmup(time);
    }

    private void getBatchStatistics() {
        List<BatchStatistics> batchStatistics = new ArrayList<>();
        batchStatistics.add(smallCenter.getBatchStatistics());
        batchStatistics.add(mediumCenter.getBatchStatistics());
        batchStatistics.add(largeCenter.getBatchStatistics());
        batchStatistics.add(rideCenter.getBatchStatistics());
    }


    private void initCenters(boolean approximateServiceAsExponential) {
        CenterFactory factory = new CenterFactory(false);
        smallCenter = factory.createSmallCenter(approximateServiceAsExponential, false);
        mediumCenter = factory.createMediumCenter(approximateServiceAsExponential, false);
        largeCenter = factory.createLargeCenter(approximateServiceAsExponential, false);
        rideCenter = factory.createRideCenter(approximateServiceAsExponential, false);
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
            case ARRIVAL_RIDE_CENTER:
                rideCenter.processArrival(event, msqTime, events);
                rideCenter.generateNextArrival(events);
                break;
            case COMPLETION_RIDE_CENTER:
                rideCenter.processCompletion(event, msqTime, events);
                break;
        }
    }

    private void modelVerification(String simulationType) {
        List<AnalyticalComputation.AnalyticalResult> analyticalResultList = computeAnalyticalResultsImproved(simulationType);

        // Compare results and verifications and save comparison result
        List<MeanStatistics> batchMeanStatisticsList = aggregateBatchMeanStatistics();

        List<Comparison.ComparisonResult> comparisonResultList = compareResults(simulationType, analyticalResultList, batchMeanStatisticsList);

        List<ConfidenceIntervals> confidenceIntervalsList = aggregateConfidenceIntervals();

        List<Verification.VerificationResult> verificationResultList = verifyConfidenceIntervals(simulationType, batchMeanStatisticsList, comparisonResultList, confidenceIntervalsList);

        printFinalResults(verificationResultList, batchSize, numBatches);
    }

    private List<MeanStatistics> aggregateBatchMeanStatistics() {
        List<MeanStatistics> batchMeanStatisticsList = new ArrayList<>();
        batchMeanStatisticsList.add(smallCenter.getBatchMeanStatistics());
        batchMeanStatisticsList.add(mediumCenter.getBatchMeanStatistics());
        batchMeanStatisticsList.add(largeCenter.getBatchMeanStatistics());
        batchMeanStatisticsList.add(rideCenter.getBatchMeanStatistics());
        return batchMeanStatisticsList;
    }

    private List<ConfidenceIntervals> aggregateConfidenceIntervals() {
        List<ConfidenceIntervals> confidenceIntervalsList = new ArrayList<>();
        confidenceIntervalsList.add(createConfidenceIntervals(smallCenter.getBatchStatistics()));
        confidenceIntervalsList.add(createConfidenceIntervals(mediumCenter.getBatchStatistics()));
        confidenceIntervalsList.add(createConfidenceIntervals(largeCenter.getBatchStatistics()));
        confidenceIntervalsList.add(createConfidenceIntervals(rideCenter.getBatchStatistics()));
        return confidenceIntervalsList;
    }

    private ConfidenceIntervals createConfidenceIntervals(BatchStatistics stats) {
        return new ConfidenceIntervals(
                stats.meanResponseTimeList, stats.meanQueueTimeList, stats.meanServiceTimeList,
                stats.meanSystemPopulationList, stats.meanQueuePopulationList, stats.meanUtilizationList, stats.lambdaList
        );
    }


    private void writeAllStats(String simulationType, long seed) {
        printDebug("Writing csv files with stats for all the centers.");
        smallCenter.writeBatchStats(simulationType, seed);
        mediumCenter.writeBatchStats(simulationType, seed);
        largeCenter.writeBatchStats(simulationType, seed);
        rideCenter.writeBatchStats(simulationType, seed);
    }

    private void updateAreas(MsqTime msqTime) {
        // Updating the areas
        smallCenter.setArea(msqTime);
        mediumCenter.setArea(msqTime);
        largeCenter.setArea(msqTime);
        rideCenter.setArea(msqTime);
    }

    private long getMinimumNumberOfJobsServedByCenters() {
        return Stream.of(
                        smallCenter.getTotalNumberOfJobsServed(),
                        mediumCenter.getTotalNumberOfJobsServed(),
                        largeCenter.getTotalNumberOfJobsServed(),
                        rideCenter.getTotalNumberOfJobsServed())
                .min(Long::compare).orElseThrow();
    }

    private boolean isDone() {
        System.out.println("[DEBUG] isDone: small=" + smallCenter.isDone()
                + ", medium=" + mediumCenter.isDone()
                + ", large=" + largeCenter.isDone()
                + ", ride=" + rideCenter.isDone());
        return smallCenter.isDone()
                && mediumCenter.isDone()
                && largeCenter.isDone()
                && rideCenter.isDone();
    }

    private void resetCenters(Rngs rngs, EventQueue events) {
        smallCenter.reset(rngs);
        //generating first arrival
        double time = smallCenter.getArrival();
        MsqEvent firstEvent = new MsqEvent(ARRIVAL_SMALL_CENTER, time);
        events.add(firstEvent);

        mediumCenter.reset(rngs);
        //generating first arrival
        time = mediumCenter.getArrival();
        firstEvent = new MsqEvent(ARRIVAL_MEDIUM_CENTER, time);
        events.add(firstEvent);

        largeCenter.reset(rngs);
        //generating first arrival
        time = largeCenter.getArrival();
        firstEvent = new MsqEvent(ARRIVAL_LARGE_CENTER, time);
        events.add(firstEvent);

        rideCenter.reset(rngs);
        //generating first arrival
        time = rideCenter.getArrival();
        firstEvent = new MsqEvent(ARRIVAL_RIDE_CENTER, time);
        events.add(firstEvent);
    }

    private void writeObservations(String path) {
        PlotUtils.writeObservations(path, smallCenterObservation);
        PlotUtils.writeObservations(path, mediumCenterObservation);
        PlotUtils.writeObservations(path, largeCenterObservation);
        PlotUtils.writeObservations(path, rideCenterObservation);
    }
}
