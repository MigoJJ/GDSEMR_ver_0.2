#!/bin/bash

# =============================================================================
# run-gradle.sh - Run Gradle project on Ubuntu
# Project: GDSEMR_ver_0.2
# Location: ~/git/GDSEMR_ver_0.2
# =============================================================================

set -euo pipefail  # Exit on error, unset vars, pipe failures

# --- Configuration ---
PROJECT_DIR="$HOME/git/GDSEMR_ver_0.2"
GRADLE_WRAPPER="./gradlew"
LOG_FILE="$PROJECT_DIR/run.log"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

# --- Colors for output ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# --- Functions ---
log() {
    echo -e "${GREEN}[INFO]${NC} $1" | tee -a "$LOG_FILE"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1" | tee -a "$LOG_FILE"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2 | tee -a "$LOG_FILE"
}

# --- Main ---
main() {
    echo "===========================================================" | tee -a "$LOG_FILE"
    echo "Gradle Project Runner - $TIMESTAMP" | tee -a "$LOG_FILE"
    echo "Project: $PROJECT_DIR" | tee -a "$LOG_FILE"
    echo "===========================================================" | tee -a "$LOG_FILE"

    # Check if in correct directory
    if [[ ! -f "$PROJECT_DIR/settings.gradle" ]]; then
        error "Not in a Gradle project root! Missing settings.gradle"
        error "Expected location: $PROJECT_DIR"
        exit 1
    fi

    cd "$PROJECT_DIR" || { error "Failed to cd into $PROJECT_DIR"; exit 1; }

    # Ensure gradlew is executable
    if [[ ! -x "$GRADLE_WRAPPER" ]]; then
        log "Making gradlew executable..."
        chmod +x "$GRADLE_WRAPPER" || { error "Failed to chmod +x gradlew"; exit 1; }
    fi

    # Optional: Clean before build (uncomment if needed)
    # log "Cleaning previous builds..."
    # "$GRADLE_WRAPPER" clean >> "$LOG_FILE" 2>&1

    # Run the default task (usually 'bootRun' or 'run' for Spring Boot)
    log "Starting Gradle build and run..."
    log "Check full log at: $LOG_FILE"

    # Detect if it's a Spring Boot app and use bootRun, else use 'run'
    if grep -q "org.springframework.boot" build.gradle 2>/dev/null || \
       grep -q "id 'org.springframework.boot'" build.gradle.kts 2>/dev/null; then
        TASK="bootRun"
    else
        TASK="run"
    fi

    log "Executing: $GRADLE_WRAPPER $TASK"

    if "$GRADLE_WRAPPER" "$TASK"; then
        log "Gradle task '$TASK' completed successfully!"
    else
        error "Gradle task '$TASK' failed. See log above."
        exit 1
    fi
}

# --- Execute ---
{
    main
} 2>&1 | tee -a "$LOG_FILE"

echo
echo "Done. Full log saved to: $LOG_FILE"
