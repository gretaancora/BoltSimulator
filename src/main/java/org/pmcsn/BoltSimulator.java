package org.pmcsn;

import java.util.Scanner;

import org.pmcsn.configuration.ConfigurationManager;
import org.pmcsn.controller.FiniteImprovedSimulationRunner;
import org.pmcsn.controller.FiniteSimulationRunner;
import org.pmcsn.controller.ModelVerificationBatchMeans;

import static org.pmcsn.utils.PrintUtils.*;

public class BoltSimulator {

    public static void main(String[] args) throws Exception {
        //FileUtils.deleteDirectory("csvFiles");
        Scanner scanner = new Scanner(System.in);

        while (true) {
            mainMenu(scanner);
        }
    }
    private static void mainMenu(Scanner scanner) throws Exception {
        resetMenu();
        printMainMenuOptions();
        int choice = scanner.nextInt();
        scanner.nextLine();  // Consume newline

        switch (choice) {
            case 1:
                startSimulation(scanner);
                break;
            case 2:
                printError("Exiting MAAC Finance Simulator. Goodbye!");
                System.exit(0);
                break;
            default:
                printError("Invalid choice '" + choice + "'. Please try again.");
                pauseAndClear(scanner);
        }
    }

    private static void startSimulation(Scanner scanner) throws Exception {
        resetMenu();
        printStartSimulationOptions();


        int simulationType = scanner.nextInt();
        scanner.nextLine();  // Consume newline
        ConfigurationManager configurationManager = new ConfigurationManager();
        boolean shouldTrackObservations = configurationManager.getBoolean("general", "shouldTrackObservations");
        FiniteSimulationRunner basicRunner = new FiniteSimulationRunner();
        FiniteImprovedSimulationRunner improvedRunner = new FiniteImprovedSimulationRunner();


        switch (simulationType) {
            case 1:
                //OK
                basicRunner.runFiniteSimulation(false, shouldTrackObservations);
                break;
            case 2:
                ModelVerificationBatchMeans.runModelWithBatchMeansMethod(); // Call BatchMeans main method
                break;
            case 3:
                ModelVerificationBatchMeans.runModelVerificationWithBatchMeansMethod(); // Call BatchMeans main method
                break;
            case 4:
                improvedRunner.runImprovedModelSimulation(false, shouldTrackObservations);
                break;
            case 5:
                ModelVerificationBatchMeans.runModelWithBatchMeansMethodImproved();
                break;
            case 6:
                ModelVerificationBatchMeans.runModelVerificationWithBatchMeansMethodImproved();
                break;
            default:
                printError("Invalid simulation type '" + simulationType + "'.");
        }
        pauseAndClear(scanner);
    }
}
