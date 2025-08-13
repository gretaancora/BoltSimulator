package org.pmcsn.controller;

import org.pmcsn.centers.*;
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



public class FiniteImprovedSimulationRunner {
    private static final ConfigurationManager config = new ConfigurationManager();
    private final int start = 0;
    private static final double stop = config.getDouble("general", "finiteSimObservationTime");
    private final long seed;
    private final int runsNumber = config.getInt("general", "runsNumber");
    private final int streamIndex = config.getInt("general", "seedStreamIndex");

    private SimpleCenter largeCenter;
    private SimpleCenter mediumCenter;
    private SimpleCenter smallCenter;
    private RideCenter rideCenter;

    private List<Observations> smallCenterObservation;
    private List<Observations> mediumCenterObservation;
    private List<Observations> largeCenterObservation;
    private List<Observations> rideCenterObservation;

    private EventQueue queue;

    public FiniteImprovedSimulationRunner() {
        this(123456789L);
    }

    public FiniteImprovedSimulationRunner(long seed) {
        this.seed = seed;
    }

    public void runImprovedModelSimulation(boolean approximateServiceAsExponential, boolean shouldTrackObservations) throws Exception {
        initCenters(approximateServiceAsExponential);
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

            if (shouldTrackObservations) {
                queue = new FiniteSimulationEventQueue();
            } else {
                queue = new EventQueue();
            }

            resetCenters(rngs);

            MsqEvent event;
            // need to use OR because all the conditions should be false
            while (smallCenter.isEndOfArrivals() || mediumCenter.isEndOfArrivals() || largeCenter.isEndOfArrivals() || !rideCenter.isEndOfArrivals() || queue.isEmpty()) {

                // Retrieving next event to be processed
                event = queue.pop();
                if (event.type == EventType.SAVE_STAT) {
                    smallCenter.updateObservations(smallCenterObservation);
                    mediumCenter.updateObservations(mediumCenterObservation);
                    largeCenter.updateObservations(largeCenterObservation);
                    rideCenter.updateObservations(rideCenterObservation);
                    continue;
                }
                msqTime.next = event.time;

                // Updating areas
                updateAreas(msqTime);

                // Advancing the clock
                msqTime.current = msqTime.next;

                // Processing the event based on its type
                processCurrentEvent(event, msqTime);

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
            rngs.selectStream(config.getInt("general", "seedStreamIndex"));
            seeds[i + 1] = rngs.getSeed();
        }

        System.out.println(simulationType + " HAS JUST FINISHED.");

        if (shouldTrackObservations) {
            PlotUtils.welchPlot(observationsPath);
        }

        // Writing statistics csv with data from all runs
        writeAllStats(simulationType, seed);

        if(approximateServiceAsExponential) {
            modelVerification(simulationType); // Computing and writing verifications stats csv
        }

        System.out.println();
        printMeanResponseTime();
    }

    private String getSimulationType(boolean approximateServiceAsExponential) {
        String simulationType;
        if (approximateServiceAsExponential) {
            simulationType = "IMPROVED_FINITE_SIMULATION_EXPONENTIAL";
        } else {
            simulationType = "IMPROVED_FINITE_SIMULATION";
        }
        return simulationType;
    }

    private void initCenters(boolean approximateServiceAsExponential) {
        CenterFactory factory = new CenterFactory(false);
        smallCenter = factory.createSmallCenter(approximateServiceAsExponential, false);
        mediumCenter = factory.createMediumCenter(approximateServiceAsExponential, false);
        largeCenter = factory.createLargeCenter(approximateServiceAsExponential, false);
        rideCenter = factory.createRideCenter(approximateServiceAsExponential, false);
    }

    private void resetCenters(Rngs rngs) {
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

        // Initialize large
        rideCenter.start(rngs, start);
        rideCenter.setStop(stop);

        //generating first arrival
        time = rideCenter.getArrival();
        firstEvent = new MsqEvent(EventType.ARRIVAL_RIDE_CENTER, time);
        firstEvent.postiRichiesti = rideCenter.getNumPosti();
        queue.add(firstEvent);
    }

    private void processCurrentEvent(MsqEvent event, MsqTime msqTime) {
        switch (event.type) {
            case ARRIVAL_SMALL_CENTER:
                smallCenter.processArrival(event, msqTime, queue);
                smallCenter.generateNextArrival(queue, ARRIVAL_SMALL_CENTER);
                break;
            case COMPLETION_SMALL_CENTER:
                smallCenter.processCompletion(event, msqTime, queue);
                break;
            case ARRIVAL_MEDIUM_CENTER:
                mediumCenter.processArrival(event, msqTime, queue);
                mediumCenter.generateNextArrival(queue, ARRIVAL_MEDIUM_CENTER);
                break;
            case COMPLETION_MEDIUM_CENTER:
                mediumCenter.processCompletion(event, msqTime, queue);
                break;
            case ARRIVAL_LARGE_CENTER:
                largeCenter.processArrival(event, msqTime, queue);
                largeCenter.generateNextArrival(queue, ARRIVAL_LARGE_CENTER);
                break;
            case COMPLETION_LARGE_CENTER:
                largeCenter.processCompletion(event, msqTime, queue);
                break;
            case ARRIVAL_RIDE_CENTER:
                rideCenter.processArrival(event, msqTime, queue);
                rideCenter.generateNextArrival(queue);
                break;
            case COMPLETION_RIDE_CENTER:
                rideCenter.processCompletion(event, msqTime, queue);
                break;
        }
    }

    private void saveAllStats() {
        smallCenter.saveStats();
        mediumCenter.saveStats();
        largeCenter.saveStats();
        rideCenter.saveStats();
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
        meanStatisticsList.add(rideCenter.getMeanStatistics());
        return meanStatisticsList;
    }

    private void printMeanResponseTime(){
        System.out.println("Average response time in SMALL CENTER: "+ smallCenter.getMeanStatistics().meanResponseTime);
        System.out.println("Average response time in MEDIUM CENTER: "+ mediumCenter.getMeanStatistics().meanResponseTime);
        System.out.println("Average response time in LARGE CENTER: " + largeCenter.getMeanStatistics().meanResponseTime);
        System.out.println("Average response time in RIDE SHARING CENTER: " + rideCenter.getMeanStatistics().meanResponseTime);
    }

    private List<ConfidenceIntervals> aggregateConfidenceIntervals() {
        List<ConfidenceIntervals> confidenceIntervalsList = new ArrayList<>();
        confidenceIntervalsList.add(createConfidenceIntervals(smallCenter.getStatistics()));
        confidenceIntervalsList.add(createConfidenceIntervals(mediumCenter.getStatistics()));
        confidenceIntervalsList.add(createConfidenceIntervals(largeCenter.getStatistics()));
        confidenceIntervalsList.add(createConfidenceIntervals(rideCenter.getStatistics()));
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
        rideCenter.writeStats(simulationType, seed);
    }

    private long getTotalNumberOfJobsInSystem() {
        return smallCenter.getNumberOfJobsInNode() +
                mediumCenter.getNumberOfJobsInNode() +
                largeCenter.getNumberOfJobsInNode() +
                rideCenter.getNumberOfJobsInNode();
    }

    private void updateAreas(MsqTime msqTime) {
        // Updating the areas
        smallCenter.setArea(msqTime);
        mediumCenter.setArea(msqTime);
        largeCenter.setArea(msqTime);
        rideCenter.setArea(msqTime);
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

    private void resetObservations() {
        smallCenterObservation.forEach(Observations::reset);
        mediumCenterObservation.forEach(Observations::reset);
        largeCenterObservation.forEach(Observations::reset);
        rideCenterObservation.forEach(Observations::reset);
    }

    private void writeObservations(String path) {
        PlotUtils.writeObservations(path, smallCenterObservation);
        PlotUtils.writeObservations(path, mediumCenterObservation);
        PlotUtils.writeObservations(path, largeCenterObservation);
        PlotUtils.writeObservations(path, rideCenterObservation);
    }
}
