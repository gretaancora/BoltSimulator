package org.pmcsn.model;

public class MsqServer {
    public double lastCompletionTime;
    public boolean running;
    public int capacita;
    public int capacitaRimanente;
    public int numRichiesteServite;
    public double svc;

    public MsqServer() {
        this.lastCompletionTime = 0;
        this.running = false;
    }

    public void reset() {
        this.lastCompletionTime = 0;
        this.running = false;
    }
}
