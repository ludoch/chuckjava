package org.chuck.audio;

/**
 * An Attack, Decay, Sustain, Release envelope generator.
 */
public class Adsr extends ChuckUGen {
    public enum State { SILENT, ATTACK, DECAY, SUSTAIN, RELEASE }
    
    private State state = State.SILENT;
    private float currentLevel = 0.0f;
    private final float sampleRate;
    
    // Default values (seconds)
    private float attackTime = 0.01f;
    private float decayTime = 0.05f;
    private float sustainLevel = 0.5f;
    private float releaseTime = 0.1f;
    
    // Increments per sample
    private float attackInc;
    private float decayInc;
    private float releaseInc;
    
    public Adsr(float sampleRate) {
        this.sampleRate = sampleRate;
        updateIncrements();
    }

    public void set(float attack, float decay, float sustain, float release) {
        this.attackTime = attack;
        this.decayTime = decay;
        this.sustainLevel = sustain;
        this.releaseTime = release;
        updateIncrements();
    }
    
    private void updateIncrements() {
        attackInc = 1.0f / (attackTime * sampleRate);
        decayInc = (1.0f - sustainLevel) / (decayTime * sampleRate);
        releaseInc = sustainLevel / (releaseTime * sampleRate);
    }
    
    public void keyOn() {
        state = State.ATTACK;
    }
    
    public void keyOff() {
        state = State.RELEASE;
    }

    @Override
    protected float compute(float input) {
        switch (state) {
            case ATTACK:
                currentLevel += attackInc;
                if (currentLevel >= 1.0f) {
                    currentLevel = 1.0f;
                    state = State.DECAY;
                }
                break;
            case DECAY:
                currentLevel -= decayInc;
                if (currentLevel <= sustainLevel) {
                    currentLevel = sustainLevel;
                    state = State.SUSTAIN;
                }
                break;
            case SUSTAIN:
                currentLevel = sustainLevel;
                break;
            case RELEASE:
                currentLevel -= releaseInc;
                if (currentLevel <= 0.0f) {
                    currentLevel = 0.0f;
                    state = State.SILENT;
                }
                break;
            case SILENT:
                currentLevel = 0.0f;
                break;
        }
        return input * currentLevel;
    }
    
    public float getCurrentLevel() {
        return currentLevel;
    }
}
