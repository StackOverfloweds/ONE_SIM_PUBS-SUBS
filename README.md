# The ONE_NEW SIM - Publish-Subscribe Simulator

## Quick Start Guide

Welcome to the ONE_NEW SIM simulator! This guide will help you set up and run the simulation efficiently.

---

## **1. Clone the Repository**

First, clone the repository to your local machine:

```bash
git clone https://github.com/StackOverfloweds/ONE_SIM_PUBS-SUBS.git
```

## **2. Running the Simulator with Gradle wih command Make**

The project uses Gradle as a build tool and Makefile for simplified execution.

### a. Build the Project

```bash
make build
```

### b. Run the Simulation

Once the build is complete, start the simulation with:

```bash
make run
```
### c. Clean the

To clean up build files and reset the project:

```bash
make run
```

## **3. Understanding the Code**

This project implements a Publish-Subscribe Model with roles assigned to nodes in the simulation.

### Roles in the Simulator

- Publisher (P): Generates and sends messages to topics.
- Subscriber (S): Subscribes to specific topics and receives messages.
- Broker (B): Acts as an intermediary between publishers and subscribers.
- Key Distribution Center (KDC): Manages authentication and encryption keys.

### Key Components

- `routing/PublishAndSubscriberRouting.java`: Implements the publish-subscribe      message routing logic.
- `KDC/Publisher/` : Contains the encryption and topic registration logic.
- `KDC/Subscriber/` : Handles subscriptions and key authentication.
- `KDC/NAKT/` : Implements the encryption-based access control mechanism.
- `core/DTNHost.java` : Manages the roles and communication between nodes.


## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
