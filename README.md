# Next-Event Simulation of Ride-Hailing and Ride-Sharing Systems

## Executive Summary

**BoltSimulator** is an academic project for the Performance Modeling of Computer Systems and Networks course at the University of Rome Tor Vergata. It models a ride-hailing / ride-sharing system using a next-event simulation approach, where simulated events are processed in event-time order rather than advancing the system in fixed time increments.

The repository contains the Java simulation sources, a set of Python graph utilities, and the project's written report and presentation. It is intended as a study and evaluation project for discrete-event simulation and performance modeling, not as a production ride-dispatch platform.

## Technical Scope & Key Findings

### Simulation approach

A next-event simulator advances the model by selecting the next scheduled event, updating the simulated system state, and processing any new events that result. For a ride-hailing / ride-sharing model, this approach is suitable for studying interactions between requests and vehicles without processing every intermediate instant in simulated time.

The project is organized around a Java implementation under `src/main/java`. The `PyGraphs/` directory provides supporting Python scripts for graphing or inspecting simulation results. The exact run configuration and interpretation of the reported metrics should be taken from the source and the accompanying report.

### Project materials

- `PresentazioneBoltSimulator.pdf` — project presentation.
- `RelazioneBoltSimulator.pdf` — project report and supporting technical discussion.
- `BoltSimulator.iml` — IntelliJ IDEA module configuration. It identifies `src/main/java` as a Java source root and records a Python 3.9 interpreter for the Python component.

## Repository Contents

| Path | Role |
|---|---|
| `src/main/java/` | Java simulation source code. |
| `src/main/resources/` | Java resources source root declared in the IDE module configuration, if present in the checkout. |
| `PyGraphs/` | Python scripts used for graph-related analysis or presentation of results. |
| `BoltSimulator.iml` | IntelliJ IDEA module configuration. |
| `PresentazioneBoltSimulator.pdf` | Project presentation. |
| `RelazioneBoltSimulator.pdf` | Project report. |

## Validation & Experimental Use

For repeatable experiments, record the simulator configuration, input parameters, software versions, and the exact scripts used to generate each graph. Check whether results are deterministic or depend on random sampling before comparing multiple runs. Where the report defines particular scenarios or performance metrics, use the same definitions when reproducing its plots.
