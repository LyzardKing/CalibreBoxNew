# Variables
PACKAGE_NAME = com.example.calibreboxnew
MAIN_ACTIVITY = com.example.calibreboxnew.MainActivity
ADB = adb
GRADLEW = ./gradlew
BUILD_TYPE = assembleRelease
APK_PATH = app/build/outputs/apk/release/app-release.apk
OUTPUT_NAME = unsigned-app.apk

.PHONY: all help clean build copy-apk

# Default target
all: build copy-apk

## help: Show available commands
help:
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//' | sed -e 's/##//'

## clean: Remove build artifacts
clean:
	@echo "Cleaning project..."
	$(GRADLEW) clean

## build: Compile the app and generate the debug APK (unsigned)
build:
	@echo "Building APK..."
	rm -f $(OUTPUT_NAME)
	$(GRADLEW) $(BUILD_TYPE)

## copy-apk: Moves the APK to the root directory for easy access
copy-apk:
	@echo "Copying APK to root..."
	cp $(APK_PATH) $(OUTPUT_NAME)
	@echo "Build successful: $(OUTPUT_NAME)"

## lint: Run static analysis
lint:
	$(GRADLEW) lint

## install: Install the generated APK to a connected device
install: build copy-apk
	@echo "Installing APK..."
	$(ADB) install -r $(OUTPUT_NAME)

## run: Install and launch the app on a connected device
run: install
	@echo "Launching $(PACKAGE_NAME)..."
	$(ADB) shell am start -n $(PACKAGE_NAME)/$(MAIN_ACTIVITY)
	@echo "Launch successful."

## logcat: View real-time logs from the app
logcat:
	$(ADB) logcat --pid=$$($(ADB) shell pidof -s $(PACKAGE_NAME))
