package org.pmcsn.configuration;

import org.pmcsn.centers.*;
import org.pmcsn.controller.BatchImprovedSimulationRunner;
import org.pmcsn.controller.FiniteImprovedSimulationRunner;

public class CenterFactory {
    private final ConfigurationManager configurationManager = new ConfigurationManager();
    private static int batchSize;
    private static int numBatches;
    private final boolean isImprovedSimulation;

    public CenterFactory(boolean isImprovedSimulation) {
        this.isImprovedSimulation = isImprovedSimulation;
        ConfigurationManager config = new ConfigurationManager();
        if (isImprovedSimulation) {
            batchSize = config.getInt("general", "batchSizeImproved");
            numBatches = config.getInt("general", "numBatchesImproved");
        }else{
            batchSize = config.getInt("general", "batchSize");
            numBatches = config.getInt("general", "numBatches");
        }
    }


    public SimpleCenter createSmallCenter(boolean approximateServiceAsExponential, boolean isBatch) {
        int serversNumber;
        double meanServiceTime;
        double sigma;
        double truncationPoint;
        if (isImprovedSimulation) {
            serversNumber = configurationManager.getInt("smallCenter", "serversNumberImproved");
            meanServiceTime = configurationManager.getDouble("smallCenter", "meanServiceTimeImproved");
            sigma = configurationManager.getDouble("smallCenter", "sigmaImproved");
            truncationPoint = configurationManager.getDouble("smallCenter", "truncationPointImproved");
        } else {
            serversNumber = configurationManager.getInt("smallCenter", "serversNumber");
            meanServiceTime = configurationManager.getDouble("smallCenter", "meanServiceTime");
            sigma = configurationManager.getDouble("smallCenter", "sigma");
            truncationPoint = configurationManager.getDouble("smallCenter", "truncationPoint");
        }
        return new SimpleCenter(
                configurationManager.getString("smallCenter", "centerName"),
                meanServiceTime,
                sigma,
                truncationPoint,
                serversNumber,
                configurationManager.getInt("smallCenter", "streamIndex"),
                approximateServiceAsExponential,
                isBatch,
                batchSize,
                numBatches);
    }


    public SimpleCenter createMediumCenter(boolean approximateServiceAsExponential, boolean isBatch) {
        int serversNumber;
        double meanServiceTime;
        double sigma;
        double truncationPoint;
        if (isImprovedSimulation) {
            serversNumber = configurationManager.getInt("mediumCenter", "serversNumberImproved");
            meanServiceTime = configurationManager.getDouble("mediumCenter", "meanServiceTimeImproved");
            sigma = configurationManager.getDouble("mediumCenter", "sigma");
            truncationPoint = configurationManager.getDouble("mediumCenter", "truncationPointImproved");
        } else {
            serversNumber = configurationManager.getInt("mediumCenter", "serversNumber");
            meanServiceTime = configurationManager.getDouble("mediumCenter", "meanServiceTime");
            sigma = configurationManager.getDouble("mediumCenter", "sigmaImproved");
            truncationPoint = configurationManager.getDouble("mediumCenter", "truncationPoint");
        }
        return new SimpleCenter(
                configurationManager.getString("mediumCenter", "centerName"),
                meanServiceTime,
                sigma,
                truncationPoint,
                serversNumber,
                configurationManager.getInt("mediumCenter", "streamIndex"),
                approximateServiceAsExponential,
                isBatch,
                batchSize,
                numBatches);
    }


    public SimpleCenter createLargeCenter(boolean approximateServiceAsExponential, boolean isBatch) {
        int serversNumber;
        double meanServiceTime;
        double sigma;
        double truncationPoint;
        if (isImprovedSimulation) {
            serversNumber = configurationManager.getInt("largeCenter", "serversNumberImproved");
            meanServiceTime = configurationManager.getDouble("largeCenter", "meanServiceTimeImproved");
            sigma = configurationManager.getDouble("largeCenter", "sigma");
            truncationPoint = configurationManager.getDouble("largeCenter", "truncationPointImproved");
        } else {
            serversNumber = configurationManager.getInt("largeCenter", "serversNumber");
            meanServiceTime = configurationManager.getDouble("largeCenter", "meanServiceTime");
            sigma = configurationManager.getDouble("largeCenter", "sigmaImproved");
            truncationPoint = configurationManager.getDouble("largeCenter", "truncationPoint");
        }
        return new SimpleCenter(
                configurationManager.getString("largeCenter", "centerName"),
                meanServiceTime,
                sigma,
                truncationPoint,
                serversNumber,
                configurationManager.getInt("largeCenter", "streamIndex"),
                approximateServiceAsExponential,
                isBatch,
                batchSize,
                numBatches);
    }

    public RideCenter createRideCenter(boolean approximateServiceAsExponential, boolean isBatch) {
        int serversNumber;
        int smallServers;
        int mediumServers;
        int largeServers;
        double meanServiceTime;
        double sigma;
        double truncationPoint;
        double matchInterval;
        int p_match_busy;
        int p_match_idle;

        serversNumber = configurationManager.getInt("rideCenter", "serversNumberImproved");
        smallServers = configurationManager.getInt("rideCenter", "smallServersNumberImproved");
        mediumServers = configurationManager.getInt("rideCenter", "mediumServersNumberImproved");
        largeServers = configurationManager.getInt("rideCenter", "largeServersNumberImproved");
        meanServiceTime = configurationManager.getDouble("rideCenter", "meanServiceTimeImproved");
        sigma = configurationManager.getDouble("rideCenter", "sigmaImproved");
        truncationPoint = configurationManager.getDouble("rideCenter", "truncationPointImproved");
        matchInterval = configurationManager.getDouble("rideCenter", "matchInterval");
        p_match_busy = configurationManager.getInt("rideCenter", "pMatchBusy");
        p_match_idle = configurationManager.getInt("rideCenter", "pMatchIdle");


        return new RideCenter(
                configurationManager.getString("rideCenter", "centerName"),
                meanServiceTime,
                sigma,
                truncationPoint,
                serversNumber,
                smallServers,
                mediumServers,
                largeServers,
                configurationManager.getInt("rideCenter", "streamIndex"),
                approximateServiceAsExponential,
                isBatch,
                batchSize,
                numBatches,
                matchInterval,
                p_match_busy,
                p_match_idle);
    }
}
