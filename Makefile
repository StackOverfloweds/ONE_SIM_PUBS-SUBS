# Nama stack
STACK_NAME := one_sim_stack

# Nama container (ganti sesuai nama container Anda)
CONTAINER_NAME := one_sim_stack_run_simulator

# Login (Inisialisasi Docker Swarm jika belum dilakukan)
start:
	@echo "Building Docker images..."
	@sudo docker compose build
	@echo "Deploying the stack using Docker Compose..."
	@sudo docker compose up -d
	@echo "Stack deployed successfully."
	@sudo docker ps
	@echo "Check container name"
	@sudo docker container ls

# Run (Menjalankan simulasi menggunakan container yang sudah berjalan)
run:
	@echo "Running simulation on container $(CONTAINER_NAME)..."
	# Menjalankan simulasi pada container yang sesuai, menggunakan ID container yang ditemukan
	@sudo docker exec -it $(shell sudo docker ps -q --filter "name=run_simulator" | head -n 1) bash /app/scripts/run.sh


# Logout (Menghapus stack)
stop:
	@echo "Stopping and removing the stack..."
	@sudo docker compose down
	@echo "Stack removed successfully."
