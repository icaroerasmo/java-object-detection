package com.icaroerasmo.runners;

import com.icaroerasmo.properties.CameraProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@code enabled} camera filtering used at startup. The helper
 * is package-private and side-effect free, so no threads or Spring context are
 * involved.
 */
class RtspRecognitionRunnerEnabledCamerasTest {

    private CameraProperties camera(String name, boolean enabled) {
        CameraProperties camera = new CameraProperties();
        camera.setName(name);
        camera.setUrl("rtsp://example.local/" + name);
        camera.setEnabled(enabled);
        return camera;
    }

    @Test
    void keepsOnlyEnabledCameras() {
        List<CameraProperties> all = Arrays.asList(
                camera("camera1", true),
                camera("camera2", false),
                camera("camera3", true));

        List<CameraProperties> enabled = RtspRecognitionRunner.enabledCameras(all);

        assertEquals(2, enabled.size());
        assertEquals("camera1", enabled.get(0).getName());
        assertEquals("camera3", enabled.get(1).getName());
    }

    @Test
    void returnsEmptyWhenEveryCameraIsDisabled() {
        List<CameraProperties> all = Arrays.asList(
                camera("camera1", false),
                camera("camera2", false));

        assertTrue(RtspRecognitionRunner.enabledCameras(all).isEmpty());
    }

    @Test
    void keepsCamerasThatLeaveEnabledUnset() {
        CameraProperties defaults = new CameraProperties();
        defaults.setName("camera1");
        defaults.setUrl("rtsp://example.local/camera1");

        List<CameraProperties> enabled = RtspRecognitionRunner.enabledCameras(List.of(defaults));

        assertEquals(1, enabled.size());
        assertEquals("camera1", enabled.get(0).getName());
    }

    @Test
    void skipsNullEntries() {
        List<CameraProperties> all = new ArrayList<>();
        all.add(camera("camera1", true));
        all.add(null);

        assertEquals(1, RtspRecognitionRunner.enabledCameras(all).size());
    }

    @Test
    void handlesNullAndEmptyInput() {
        assertTrue(RtspRecognitionRunner.enabledCameras(null).isEmpty());
        assertTrue(RtspRecognitionRunner.enabledCameras(List.of()).isEmpty());
    }
}
