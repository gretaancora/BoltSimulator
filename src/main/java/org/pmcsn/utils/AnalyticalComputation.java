package org.pmcsn.utils;

import org.pmcsn.configuration.ConfigurationManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.pmcsn.utils.PrintUtils.printDebug;

public class AnalyticalComputation {
    private static final ConfigurationManager config = new ConfigurationManager();

    public static class AnalyticalResult {
        public String name;
        public double lambda;
        public double rho;
        public double Etq;
        public double Enq;
        public double Ets;
        public double Ens;
        public double Es;

        public AnalyticalResult(double lambda, double rho, double Etq, double Enq, double Ets, double Ens, String name, double Es) {
            this.lambda = lambda;
            this.rho = rho;
            this.Etq = Etq;
            this.Enq = Enq;
            this.Ets = Ets;
            this.Ens = Ens;
            this.name = name;
            this.Es = Es;
        }
    }

    public static double factorial(int n) {
        double fact = 1;
        for (int i = 1; i <= n; i++) {
            fact *= i;
        }
        return fact;
    }

    // Method to calculate p(0)
    public static double calculateP0(int m, double rho) {
        double sum = 0.0;
        for (int i = 0; i < m; i++) {
            sum += Math.pow(m * rho, i) / factorial(i);
        }
        sum += (Math.pow(m * rho, m) / (factorial(m) * (1 - rho)));
        return 1 / sum;
    }

    // Method to calculate Pq
    public static double calculatePq(int m, double rho, double p0) {
        double numerator = Math.pow(m * rho, m);
        double denominator = factorial(m) * (1 - rho);
        return (numerator / denominator) * p0;
    }

    public static AnalyticalResult multiServer(String centerName, double lambda, double Esi, int numServers) {
        double Es = Esi / numServers;
        double rho = lambda * Es;
        double Etq, Enq, Ets, Ens;
        if (rho >= 1) {
            Etq = Double.POSITIVE_INFINITY;
            Enq = Double.POSITIVE_INFINITY;
            Ets = Double.POSITIVE_INFINITY;
            Ens = Double.POSITIVE_INFINITY;
        } else {
            double p0 = calculateP0(numServers, rho);
            double Pq = calculatePq(numServers, rho, p0);
            Etq = (Pq * Es) / (1 - rho);
            Enq = Etq * lambda;
            Ets = Etq + Esi;
            Ens = Ets * lambda;
        }
        return new AnalyticalResult(lambda, rho, Etq, Enq, Ets, Ens, centerName, Esi);
    }

    public static List<AnalyticalResult> computeAnalyticalResults(String simulationType) {
        printDebug("Computing analytical results for simulation...");
        List<AnalyticalResult> analyticalResults = new ArrayList<>();
        ConfigurationManager conf = new ConfigurationManager();

        /*double pFeedback = config.getDouble("simpleCenter", "pFeedback");
        //double pFeedback = 0.05;
        double pAcceptSysScoring = conf.getDouble("sysScoringAutomaticoSANTANDER", "pAccept");

        double gamma = 1 / config.getDouble("general", "interArrivalTime");
        double lambda = gamma / (1 - (pFeedback * pAcceptSysScoring));*/
        double lambda = -1; //da rivedere calcolo lambda

        analyticalResults.add(multiServer(
                config.getString("smallCenter", "centerName"),
                lambda,
                config.getDouble("smallCenter", "meanServiceTime"),
                config.getInt("smallCenter", "serversNumber")));

        analyticalResults.add(multiServer(
                config.getString("mediumCenter", "centerName"),
                lambda,
                config.getDouble("mediumCenter", "meanServiceTime"),
                config.getInt("mediumCenter", "serversNumber")));

        analyticalResults.add(multiServer(
                config.getString("largeCenter", "centerName"),
                lambda,
                config.getDouble("largeCenter", "meanServiceTime"),
                config.getInt("largeCenter", "serversNumber")));


        return(analyticalResults);
    }


    public static List<AnalyticalResult> computeAnalyticalResultsImproved(String simulationType) {
        printDebug("Computing analytical results for simulation...");
        List<AnalyticalResult> analyticalResults = new ArrayList<>();
        ConfigurationManager conf = new ConfigurationManager();

        double pFeedback = config.getDouble("comitatoCreditoSANTANDER", "pFeedback");
        double pAcceptSysScoring = 0.82;
        double pAcceptCredito = conf.getDouble("comitatoCreditoSANTANDER", "pAccept");
        double pAcceptPreScoring = 0.51;

        double gamma = 1 / config.getDouble("general", "interArrivalTime");
        double lambda = (gamma*pAcceptPreScoring) / (1 - (pFeedback * pAcceptSysScoring));

        analyticalResults.add(multiServer(
                config.getString("smallCenter", "centerName"),
                lambda,
                config.getDouble("smallCenter", "meanServiceTime"),
                config.getInt("smallCenter", "serversNumber")));

        analyticalResults.add(multiServer(
                config.getString("mediumCenter", "centerName"),
                lambda,
                config.getDouble("mediumCenter", "meanServiceTime"),
                config.getInt("mediumCenter", "serversNumber")));

        analyticalResults.add(multiServer(
                config.getString("largeCenter", "centerName"),
                lambda,
                config.getDouble("largeCenter", "meanServiceTime"),
                config.getInt("largeCenter", "serversNumber")));

        analyticalResults.add(multiServer(
                config.getString("rideCenter", "centerName"),
                lambda,
                config.getDouble("rideCenter", "meanServiceTimeImproved"),
                config.getInt("rideCenter", "serversNumberImproved")));

        writeAnalyticalResults(simulationType, analyticalResults);

        return(analyticalResults);
    }

    public static void writeAnalyticalResults(String simulationType, List<AnalyticalResult> results){
        File file = new File("csvFiles/"+simulationType+"/analyticalResults/" );
        if (!file.exists()) {
            file.mkdirs();
        }

        file = new File("csvFiles/"+simulationType+"/analyticalResults/analyticalResults.csv");
        try(FileWriter fileWriter = new FileWriter(file)) {

            String DELIMITER = "\n";
            String COMMA = ",";


            fileWriter.append("Center, E[Ts], E[Tq], E[s], E[Ns], E[Nq], ρ, λ").append(DELIMITER);
            for (AnalyticalResult result : results){

                fileWriter.append(result.name).append(COMMA)
                        .append(String.valueOf(result.Ets)).append(COMMA)
                        .append(String.valueOf(result.Etq)).append(COMMA)
                        .append(String.valueOf(result.Es)).append(COMMA)
                        .append(String.valueOf(result.Ens)).append(COMMA)
                        .append(String.valueOf(result.Enq)).append(COMMA)
                        .append(String.valueOf(result.rho)).append(COMMA)
                        .append(String.valueOf(result.lambda)).append(DELIMITER);
            }

            fileWriter.flush();
        } catch (IOException e) {
            //ignore
        }
    }

    public static void main(String[] args) {
        FileUtils.deleteDirectory("csvFiles");
        String simulationType = "ANALYTICAL";
        //writeAnalyticalResults(simulationType, computeAnalyticalResults(simulationType));
        writeAnalyticalResults(simulationType, computeAnalyticalResultsImproved(simulationType));
    }

}
