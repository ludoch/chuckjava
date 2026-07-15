package org.chuck.audio.backend;

import org.chuck.audio.AudioSampleFormat;

/** Immutable configuration snapshot requested for an audio stream. */
public record AudioStreamConfig(
    String outputDeviceName,
    String inputDeviceName,
    int sampleRate,
    int numOutputChannels,
    int numInputChannels,
    int bufferSize,
    int numBuffers,
    AudioSampleFormat sampleFormat,
    boolean minimizeLatency,
    boolean scheduleRealtime) {}
