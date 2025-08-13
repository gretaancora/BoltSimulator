package org.pmcsn.centers;

import org.pmcsn.configuration.ConfigurationManager;
import org.pmcsn.libraries.Rngs;
import org.pmcsn.model.*;

import static org.pmcsn.utils.Distributions.*;

public class SimpleCenter extends MultiServer{
    private final double sigma;
    private final double truncationPoint;
    private double sarrival;
    private double STOP = Double.POSITIVE_INFINITY;
    private boolean isEndOfArrivals = false;
    public int feedback = 0;


    public SimpleCenter(String centerName, double meanServiceTime, double sigma, double truncationPoint, int serversNumber, int streamIndex, boolean approximateServiceAsExponential, boolean isBatch, int batchSize, int numBatches) {
        super(centerName, meanServiceTime, serversNumber, streamIndex, approximateServiceAsExponential, isBatch, batchSize, numBatches);
        this.sigma = sigma;
        this.truncationPoint = truncationPoint;
    }

    @Override
    public void spawnCompletionEvent(MsqTime time, EventQueue queue, int serverId, MsqEvent currEvent) {
        double service = getService(streamIndex);
        //generate a new completion event
        MsqEvent event = null;
        switch (currEvent.type){
            case ARRIVAL_SMALL_CENTER, COMPLETION_SMALL_CENTER -> event = new MsqEvent(EventType.COMPLETION_SMALL_CENTER, time.current + service, service, serverId);
            case ARRIVAL_MEDIUM_CENTER, COMPLETION_MEDIUM_CENTER -> event = new MsqEvent(EventType.COMPLETION_MEDIUM_CENTER, time.current + service, service, serverId);
            case ARRIVAL_LARGE_CENTER, COMPLETION_LARGE_CENTER -> event = new MsqEvent(EventType.COMPLETION_LARGE_CENTER, time.current + service, service, serverId);
        }
        queue.add(event);
    }

    @Override
    double getService(int streamIndex) {
        rngs.selectStream(streamIndex);
        double serviceTime;
        if(approximateServiceAsExponential){
            serviceTime = exponential(meanServiceTime, rngs);
        } else {
            serviceTime = truncatedNormal(meanServiceTime, sigma, truncationPoint, rngs);
        }
        return serviceTime;
    }

    @Override
    public void processCompletion(MsqEvent completion, MsqTime time, EventQueue queue) {
        super.processCompletion(completion, time, queue);
        if (completion.isFeedback) {
            feedback++;
        }
    }

    public boolean isEndOfArrivals() {
        return !isEndOfArrivals;
    }


    public void start(Rngs rngs, double sarrival){
        this.rngs = rngs;
        this.sarrival = sarrival;
        reset(rngs);
    }

    public void setStop(double stop) {
        this.STOP = stop;
    }

    public double getArrival() {
        ConfigurationManager config = new ConfigurationManager();
        rngs.selectStream(streamIndex + 1);
        sarrival += exponential(config.getDouble("general", "interArrivalTime"), rngs);
        return sarrival;
    }

    public void generateNextArrival(EventQueue queue, EventType arrivalType) {
        double time = getArrival();
        if (time > STOP) {
            isEndOfArrivals = true;
        } else {
            MsqEvent event = null;
            switch (arrivalType){
                case ARRIVAL_SMALL_CENTER -> event = new MsqEvent(EventType.ARRIVAL_SMALL_CENTER, time);
                case ARRIVAL_MEDIUM_CENTER -> event = new MsqEvent(EventType.ARRIVAL_MEDIUM_CENTER, time);
                case ARRIVAL_LARGE_CENTER -> event = new MsqEvent(EventType.ARRIVAL_LARGE_CENTER, time);
            }
            queue.add(event);
        }
    }
}
