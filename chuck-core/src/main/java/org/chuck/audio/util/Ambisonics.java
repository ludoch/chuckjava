package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/** Ambisonics: First-order Ambisonic (B-format) Encoder and Decoder. */
public class Ambisonics {

  /** Encodes a mono source into B-format (W, X, Y, Z). */
  @doc("Encodes a mono source into first-order Ambisonic B-format (4 channels: W,X,Y,Z).")
  public static class Encoder extends MultiChannelUGen {
    private float azimuth = 0.0f; // degrees
    private float elevation = 0.0f; // degrees

    public Encoder() {
      super(4); // W, X, Y, Z
    }

    public void azimuth(float a) {
      this.azimuth = a;
    }

    public void elevation(float e) {
      this.elevation = e;
    }

    @Override
    protected void computeMulti(float input, long systemTime) {
      double aRad = Math.toRadians(azimuth);
      double eRad = Math.toRadians(elevation);

      float cosE = (float) Math.cos(eRad);

      lastOutChannels[0] = input * 0.7071f; // W (omni)
      lastOutChannels[1] = input * cosE * (float) Math.cos(aRad); // X (front)
      lastOutChannels[2] = input * cosE * (float) Math.sin(aRad); // Y (left)
      lastOutChannels[3] = input * (float) Math.sin(eRad); // Z (up)

      lastOut = lastOutChannels[0];
    }
  }

  /** Decodes B-format audio to specific speaker layouts. */
  @doc("Decodes first-order Ambisonic B-format to speaker layouts (Stereo, Quad, etc.).")
  public static class Decoder extends MultiChannelUGen {
    private int layout = 0; // 0: Stereo, 1: Quad

    public Decoder() {
      super(2); // Default to stereo output
    }

    public void layout(int l) {
      this.layout = l;
      this.numOutputs = (l == 1) ? 4 : 2;
      this.lastOutChannels = new float[this.numOutputs];
    }

    @Override
    protected void computeMulti(float input, long systemTime) {
      if (sources.isEmpty()) return;
      ChuckUGen src = sources.get(0);

      float w = src.getChannelLastOut(0);
      float x = src.getChannelLastOut(1);
      float y = src.getChannelLastOut(2);
      float z = src.getChannelLastOut(3);

      if (layout == 0) { // Stereo
        lastOutChannels[0] = w + y; // Left
        lastOutChannels[1] = w - y; // Right
      } else if (layout == 1) { // Quad
        lastOutChannels[0] = w + x + y; // LF
        lastOutChannels[1] = w + x - y; // RF
        lastOutChannels[2] = w - x + y; // LB
        lastOutChannels[3] = w - x - y; // RB
      }

      lastOut = lastOutChannels[0];
    }
  }
}
