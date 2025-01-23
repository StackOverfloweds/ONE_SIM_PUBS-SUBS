# The ONE_NEW_SIM - Simulator

## Step-by-Step Guide to Run the Simulator

This guide will help you to set up and run the `The ONE_NEW_SIM` simulator.

---

### 1. Clone the Repository

Start by cloning the repository to your local system using the following command:

```bash
git clone https://github.com/StackOverfloweds/ONE_SIM_PUBS-SUBS
```
---

### 2. Running the Simulator with Docker

To simplify the setup, you can run the simulator using Docker.

#### a. Use the Provided Makefile
Navigate to the project directory where the `Makefile` is located and use the following commands:

- To build the Docker image:
```bash
make build
```

- To initialize the Docker Swarm and deploy the stack:
```bash
make login
```

- To run the simulation inside the container:
```bash
make run
```

- To check the logs of the services in the stack:
```bash
make log
```

- To remove the stack after completion:
```bash
make logout
```
