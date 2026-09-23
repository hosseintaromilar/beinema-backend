package com.coupleai.coupleai.beinema.governance;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "beinema.governance")
public class GovernanceProperties {

    private int pauseMinSeconds = 30;
    private int pauseMaxSeconds = 300;
    private int pauseDefaultSeconds = 45;

    public int getPauseMinSeconds() {
        return pauseMinSeconds;
    }

    public void setPauseMinSeconds(int pauseMinSeconds) {
        this.pauseMinSeconds = pauseMinSeconds;
    }

    public int getPauseMaxSeconds() {
        return pauseMaxSeconds;
    }

    public void setPauseMaxSeconds(int pauseMaxSeconds) {
        this.pauseMaxSeconds = pauseMaxSeconds;
    }

    public int getPauseDefaultSeconds() {
        return pauseDefaultSeconds;
    }

    public void setPauseDefaultSeconds(int pauseDefaultSeconds) {
        this.pauseDefaultSeconds = pauseDefaultSeconds;
    }
}
