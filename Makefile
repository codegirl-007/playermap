# Fabric Loom 1.19+ needs Java 25. Override if yours is elsewhere:
#   make build JAVA_HOME=/path/to/jdk-25
JAVA_HOME ?= /usr/lib/jvm/java-25-openjdk
export JAVA_HOME

GRADLE := ./gradlew
GRADLE_FLAGS ?= --no-daemon

JAR := build/libs/playersmap-1.0.0.jar
PRISM_MODS_DIR := $(HOME)/.local/share/PrismLauncher/instances/1.21.11(1)/minecraft/mods
# Parentheses in instance name require quoting in shell recipes.

.PHONY: build clean run copy

build:
	$(GRADLE) build $(GRADLE_FLAGS)

clean:
	$(GRADLE) clean $(GRADLE_FLAGS)

run:
	$(GRADLE) runClient $(GRADLE_FLAGS)

copy: build
	cp $(JAR) "$(PRISM_MODS_DIR)/"
