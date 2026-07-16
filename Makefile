.PHONY: all server client clean run deploy

all: server client

server:
	@echo "Building Android Server..."
	cd android-server && ./build.sh

client:
	@echo "Building Native Client..."
	mkdir -p native-client/build
	cd native-client/build && cmake .. -DCMAKE_BUILD_TYPE=Release && make -j$$(sysctl -n hw.ncpu)

deploy: server
	@echo "Deploying to device..."
	adb push android-server/server.jar /data/local/tmp/linktomac-server.jar

run: all
	@echo "Starting LinktoMac..."
	PYTHONPATH=$$(pwd)/orchestrator python3 -m linktomac.cli

clean:
	rm -rf android-server/build
	rm -f android-server/server.jar
	rm -rf native-client/build
