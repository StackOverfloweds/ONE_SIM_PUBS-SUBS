.PHONY: build run clean

# Build project using Gradle
build:
	gradle clean build

# Run the application with specific arguments
run: build
	gradle run --args=" 1 ./conf/PublishAndSubsc_Setting.txt" --stacktrace

# Clean the project
clean:
	gradle clean
