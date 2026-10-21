.PHONY: all app client clean run deploy export stitch sync release

all: app client

app:
	@echo "Building Android App..."
	cd android-app && ./gradlew assembleDebug

client:
	@echo "Building Native Client..."
	mkdir -p native-client/build
	cd native-client/build && cmake .. -DCMAKE_BUILD_TYPE=Release && cmake --build . --parallel

deploy: app
	@echo "Installing Android App to device..."
	adb install -r android-app/app/build/outputs/apk/debug/app-debug.apk

run: client
	@echo "Starting LinktoMac..."
	PYTHONPATH=$$(pwd)/orchestrator python3 -m linktomac.cli

clean:
	cd android-app && ./gradlew clean
	rm -rf native-client/build build dist

export:
	python3 scripts/export_all_code.py

stitch:
	python3 scripts/stitch_allcode.py

sync:
	python3 scripts/update_allcode_bundles.py

release:
	bash scripts/make_release.sh
