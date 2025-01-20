# Nama stack
STACK_NAME := one_sim_stack

# Nama container (ganti sesuai nama container Anda)
CONTAINER_NAME := one_sim_stack_run_simulator.1.8s5lkfaia5arxxmn7ale8xin4

# Build (Membangun image Docker)
build: 
	@echo "Building Docker images..."
	@sudo docker compose build

# Login (Inisialisasi Docker Swarm jika belum dilakukan)
login:
	@echo "Initializing Docker Swarm..."
	# Inisialisasi Docker Swarm jika belum diinisialisasi
	@sudo docker swarm init || echo "Swarm already initialized"
	@echo "Docker Swarm initialized successfully (if not done previously)."
	@sudo docker stack deploy -c docker-compose.yml one_sim_stack
	@sudo docker stack services one_sim_stack

container:
	@echo "check container name"
	@sudo docker container ls -a

# Run (Menjalankan simulasi menggunakan container yang sudah berjalan)
run:
	@echo "Running simulation on container $(CONTAINER_NAME)..."
	@sudo docker exec -it $(CONTAINER_NAME) bash /app/scripts/run.sh


# Melihat log dari layanan tertentu (ubah sesuai nama layanan Anda)
log:
	@echo "Viewing logs for the services in the stack..."
	# Melihat log dari main_container
	@sudo docker service logs $(STACK_NAME)_main_container --follow
	@echo "Logs displayed successfully."

# Logout (Menghapus stack)
logout:
	@echo "Removing the stack..."
	@sudo docker stack rm $(STACK_NAME)
	@echo "Stack removed successfully."
