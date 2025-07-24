package org.pmcsn.model;

public class MsqEvent {
    public double service;
    public double time;   //time
    public EventType type; //type
    public int serverId;
    public int nodeId;
    public boolean isFeedback;
    public int postiRichiesti;

    public MsqEvent(EventType type, double time, double service, int serverId, int nodeId) {
        this.type = type;
        this.service = service;
        this.time = time;
        this.serverId = serverId;
        this.nodeId = nodeId;
    }

    public MsqEvent(EventType type, double time, int serverId) {
        this(type, time, 0, serverId, 0);
    }

    public MsqEvent(EventType type, double time) {
        this(type, time, 0);
    }

    public MsqEvent(EventType type, double time, double service, int serverId) {
        this(type, time, service, serverId, 0);
    }

    public MsqEvent(EventType type, double time, double service) {
        this(type, time, service, 0, 0);
    }

    public MsqEvent(EventType eventType) {
        this(eventType, 0, 0);
    }

    public double getTime(){
        return time;
    }
}
