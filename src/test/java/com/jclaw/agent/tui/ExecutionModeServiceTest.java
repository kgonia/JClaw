package com.jclaw.agent.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionModeServiceTest {

    @Test
    void startsInBuildModeAndSwitchesBackWithoutConfirmation() {
        ExecutionModeService service = new ExecutionModeService();

        assertEquals(ExecutionMode.BUILD, service.currentMode());
        assertFalse(service.isAwaitingBuildConfirmation());

        ExecutionModeService.BuildSwitchResult alreadyBuild = service.requestBuildMode();
        assertTrue(alreadyBuild.alreadyActive());

        service.switchToPlanMode();
        assertEquals(ExecutionMode.PLAN, service.currentMode());

        ExecutionModeService.BuildSwitchResult backToBuild = service.requestBuildMode();
        assertTrue(backToBuild.switched());
        assertEquals(ExecutionMode.BUILD, service.currentMode());
    }
}
