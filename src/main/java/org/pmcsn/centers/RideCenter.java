package org.pmcsn.centers;

import org.pmcsn.configuration.ConfigurationManager;
import org.pmcsn.libraries.Rngs;
import org.pmcsn.model.*;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import static org.pmcsn.model.EventType.*;
import static org.pmcsn.utils.Distributions.exponential;
import static org.pmcsn.utils.Distributions.truncatedLogNormal;

public class RideCenter extends MultiServer{
    private final double sigma;
    private final double truncationPoint;
    private double sarrival;
    private double STOP = Double.POSITIVE_INFINITY;
    private boolean isEndOfArrivals = false;
    private final double matchInterval;
    private double lastMatchTime = Double.NEGATIVE_INFINITY;
    private final Queue<MsqEvent> pendingArrivals = new LinkedList<>();
    private final int p_match_busy;
    private final int p_match_idle;
    int smallServers;
    int mediumServers;
    int largeServers;

    public RideCenter(String centerName, double meanServiceTime, double sigma, double truncationPoint, int serversNumber, int smallServers, int mediumServers, int largeServers, int streamIndex, boolean approximateServiceAsExponential, boolean isBatch, int batchSize, int numBatches, double matchInterval, int p_match_busy, int p_match_idle) {
        super(centerName, meanServiceTime, serversNumber, streamIndex, approximateServiceAsExponential, isBatch, batchSize, numBatches);
        this.sigma = sigma;
        this.truncationPoint = truncationPoint;
        this.matchInterval = matchInterval;
        this.p_match_busy = p_match_busy;
        this.p_match_idle = p_match_idle;
        this.smallServers = smallServers;
        this.mediumServers = mediumServers;
        this.largeServers = largeServers;

        for (int i=0; i<serversNumber; i++){
            if (i<smallServers){
                servers[i].capacita = 3;
                servers[i].capacitaRimanente = 3;
            } else if (i< smallServers+mediumServers) {
                servers[i].capacita = 4;
                servers[i].capacitaRimanente = 4;
            }else{
                servers[i].capacita = 8;
                servers[i].capacitaRimanente = 8;
            }
            servers[i].numRichiesteServite = 0;
            servers[i].running = false;
            servers[i].svc = 0;
            servers[i].lastCompletionTime = 0;
        }
    }

    @Override
    public void spawnCompletionEvent(MsqTime time, EventQueue queue, int serverId, MsqEvent currEvent) {
        double service = getService(streamIndex);
        MsqEvent event;

        //generate a new completion event
        if (servers[serverId].running){
            queue.removeCompletionsFor(serverId);
            double avgService = (servers[serverId].svc * servers[serverId].numRichiesteServite + service) / (servers[serverId].numRichiesteServite+1);
            servers[serverId].svc = avgService;
            event = new MsqEvent(EventType.COMPLETION_RIDE_CENTER, time.current + avgService, avgService, serverId);
        }else {
            event = new MsqEvent(EventType.COMPLETION_RIDE_CENTER, time.current + service, service, serverId);
            servers[serverId].running = true;
        }

        servers[serverId].capacitaRimanente -= currEvent.postiRichiesti;
        servers[serverId].numRichiesteServite ++;

        queue.add(event);
    }

    @Override
    double getService(int streamIndex) {
        rngs.selectStream(streamIndex);
        double serviceTime;
        if(approximateServiceAsExponential){
            serviceTime = exponential(meanServiceTime, rngs);
        } else {
            serviceTime = truncatedLogNormal(meanServiceTime, sigma, truncationPoint, rngs);
        }
        return serviceTime;
    }

    public boolean isEndOfArrivals() {
        return isEndOfArrivals;
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

    public void generateNextArrival(EventQueue queue) {
        double time = getArrival();
        if (time > STOP) {
            isEndOfArrivals = true;
        } else {
            MsqEvent event = new MsqEvent(EventType.ARRIVAL_RIDE_CENTER, time);
            event.postiRichiesti = getNumPosti();
            queue.add(event);
        }
    }

    @Override
    public void processArrival(MsqEvent arrival, MsqTime time, EventQueue queue){
        // increment the number of jobs in the node
        numberOfJobsInNode++;

        // Updating the first arrival time (we will use it in the statistics)
        if(firstArrivalTime == Double.NEGATIVE_INFINITY){
            firstArrivalTime = arrival.time;
        }
        lastArrivalTime = arrival.time;

        pendingArrivals.add(arrival);
        // se sono passati abbastanza secondi, faccio il matching su tutta la coda
        if (lastMatchTime == Double.NEGATIVE_INFINITY) {
            // primo matching: sincronizzo lastMatchTime al primo arrivo
            lastMatchTime = arrival.time;
        }

        // se ho superato l'intervallo
        if (arrival.time >= lastMatchTime + matchInterval) {
            doMatching(time, queue);
            // aggiorno lastMatchTime per il prossimo ciclo
            lastMatchTime += matchInterval;
        }
    }

    private void doMatching(MsqTime time, EventQueue queue) {
        while (true) {
            int matched = findOne(time, queue);
            if (matched == 0) {
                if (!pendingArrivals.isEmpty()) {
                    numberOfJobsInNode --;
                    MsqEvent toFb = pendingArrivals.poll();
                    generateFeedback(toFb, queue);
                }
                break;
            }
        }
    }

    private void generateFeedback(MsqEvent oldEvent, EventQueue queue) {
        // scegli il tipo in base ai posti richiesti
        EventType nextType;
        if (oldEvent.postiRichiesti <= 3) {
            nextType = ARRIVAL_SMALL_CENTER;
        } else if (oldEvent.postiRichiesti <= 4) {
            nextType = ARRIVAL_MEDIUM_CENTER;
        } else {
            nextType = ARRIVAL_LARGE_CENTER;
        }
        // crea un nuovo evento di arrivo
        MsqEvent fb = new MsqEvent(nextType, oldEvent.time);
        fb.postiRichiesti = oldEvent.postiRichiesti;
        queue.add(fb);
    }

    @Override
    public int findOne(MsqTime time, EventQueue queue) {
        if (pendingArrivals.isEmpty()) return 0;

        // 1. Prendo la PRIMA richiesta in coda
        MsqEvent firstReq = pendingArrivals.peek();

        // 2. CERCO best‑fit tra i server *attivi*
        int bestActive = -1;
        double bestCapActive = -1;

        rngs.selectStream(3);
        for (int i = 0; i < SERVERS; i++) {
            if (servers[i].running
                    && servers[i].capacitaRimanente >= firstReq.postiRichiesti
                    && rngs.random() < p_match_busy /*indica la probabilità che sto nel percorso giusto*/
                    && servers[i].capacitaRimanente > bestCapActive) {
                bestCapActive = servers[i].capacitaRimanente;
                bestActive = i;
            }
        }

        if (bestActive != -1) {
            // 2.a Assegno *solo* la prima richiesta a questo server
            spawnCompletionEvent(time, queue, bestActive, firstReq);
            pendingArrivals.poll();
            return 1;
        }

        // 3. FALLBACK interno: best‑fit tra server *inattivi*
        int bestIdle = -1; double bestCapIdle = -1;
        rngs.selectStream(4);
        for (int i = 0; i < SERVERS; i++) {
            if (!servers[i].running
                    && servers[i].capacitaRimanente >= firstReq.postiRichiesti
                    && rngs.random() < p_match_idle
                    && servers[i].capacitaRimanente > bestCapIdle) {
                bestCapIdle = servers[i].capacitaRimanente;
                bestIdle = i;
            }
        }
        if (bestIdle == -1) {
            return 0;  // né attivi né inattivi hanno accettato
        }

        // 3.a Attivo il server e *accorpo* quante richieste posso
        servers[bestIdle].running = true;
        spawnCompletionEvent(time, queue, bestIdle, firstReq);
        pendingArrivals.poll();
        int totalMatched = 0;
        Iterator<MsqEvent> it = pendingArrivals.iterator();
        while (it.hasNext()) {
            MsqEvent req = it.next();
            if (req.postiRichiesti <= servers[bestIdle].capacitaRimanente) {
                spawnCompletionEvent(time, queue, bestIdle, firstReq);
                it.remove();
                totalMatched++;
                if (servers[bestIdle].capacitaRimanente == 0) break;
            }
        }
        return totalMatched; //totale di richieste matchate
    }

    //da rivedere
    @Override
    public void processCompletion(MsqEvent completion, MsqTime time, EventQueue queue) {
        numberOfJobsInNode -= servers[completion.serverId].numRichiesteServite;

        if(!isDone()){
            totalNumberOfJobsServed += servers[completion.serverId].numRichiesteServite;
            jobServedPerBatch += servers[completion.serverId].numRichiesteServite;
        }

        int serverId = completion.serverId;
        sum[serverId].service += completion.service;
        sum[serverId].served++;
        lastCompletionTime = completion.time;
        if (!warmup && jobServedPerBatch == batchSize) {
            saveBatchStats(time);
        }

        servers[serverId].lastCompletionTime = completion.time;
        servers[serverId].running = false;

        if(!isBatch || (!warmup && !isDone())) totJobs += servers[completion.serverId].numRichiesteServite;

        //resetto lo stato
        servers[completion.serverId].numRichiesteServite = 0;
        servers[completion.serverId].running = false;
        servers[completion.serverId].svc = 0;
        servers[completion.serverId].capacitaRimanente = servers[completion.serverId].capacita;
    }


    public int getNumPosti() {
        rngs.selectStream(4);
        double r = rngs.random();
        if (r < 0.4) return 1;
        if (r < 0.7) return 2;
        if (r < 0.9) return 3;
        return 4;
    }
}
