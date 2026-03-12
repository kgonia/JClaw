package com.jclaw.agent.tui;

import java.util.Objects;

public class ExecutionModeService {

    private ExecutionMode mode = ExecutionMode.BUILD;
    private boolean buildConfirmed = true;
    private boolean awaitingBuildConfirmation;

    public synchronized ExecutionMode currentMode() {
        return mode;
    }

    public synchronized BuildSwitchResult requestBuildMode() {
        if (mode == ExecutionMode.BUILD) {
            return BuildSwitchResult.alreadyInBuild();
        }
        if (!buildConfirmed) {
            awaitingBuildConfirmation = true;
            return BuildSwitchResult.confirmationPending();
        }

        mode = ExecutionMode.BUILD;
        awaitingBuildConfirmation = false;
        return BuildSwitchResult.switchedTo(mode);
    }

    public synchronized BuildSwitchResult confirmBuildMode() {
        buildConfirmed = true;
        mode = ExecutionMode.BUILD;
        awaitingBuildConfirmation = false;
        return BuildSwitchResult.switchedTo(mode);
    }

    public synchronized ExecutionMode switchToPlanMode() {
        mode = ExecutionMode.PLAN;
        awaitingBuildConfirmation = false;
        return mode;
    }

    public synchronized boolean isAwaitingBuildConfirmation() {
        return awaitingBuildConfirmation;
    }

    public synchronized boolean isBuildConfirmed() {
        return buildConfirmed;
    }

    public record BuildSwitchResult(Status status, ExecutionMode mode) {

        public BuildSwitchResult {
            Objects.requireNonNull(status, "status");
            mode = mode == null ? ExecutionMode.PLAN : mode;
        }

        public boolean confirmationRequired() {
            return status == Status.CONFIRMATION_REQUIRED;
        }

        public boolean switched() {
            return status == Status.SWITCHED;
        }

        public boolean alreadyActive() {
            return status == Status.ALREADY_ACTIVE;
        }

        public static BuildSwitchResult confirmationPending() {
            return new BuildSwitchResult(Status.CONFIRMATION_REQUIRED, ExecutionMode.PLAN);
        }

        public static BuildSwitchResult switchedTo(ExecutionMode mode) {
            return new BuildSwitchResult(Status.SWITCHED, mode);
        }

        public static BuildSwitchResult alreadyInBuild() {
            return new BuildSwitchResult(Status.ALREADY_ACTIVE, ExecutionMode.BUILD);
        }
    }

    public enum Status {
        CONFIRMATION_REQUIRED,
        SWITCHED,
        ALREADY_ACTIVE
    }
}
