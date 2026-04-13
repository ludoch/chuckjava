package org.chuck.audio.util;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * Broadcaster: Real-time HTTP Audio Streamer.
 * Exposes a live WAV/PCM stream at http://localhost:PORT/stream.wav
 */
@doc("Broadcasts audio over the network via HTTP.")
public class Broadcaster extends ChuckUGen implements AutoCloseable {
  private HttpServer server;
  private final ConcurrentLinkedQueue<byte[]> audioQueue = new ConcurrentLinkedQueue<>();
  private final int port;
  private boolean active = false;

  private final byte[] pcmBuffer;
  private int pcmIdx = 0;
  private static final int BUFFER_CHUNK_SAMPLES = 1024;

  public Broadcaster() {
    this(8080);
  }

  public Broadcaster(int port) {
    this.port = port;
    this.pcmBuffer = new byte[BUFFER_CHUNK_SAMPLES * 2 * 2]; // 16-bit, Stereo
    this.numInputs = 2;
  }

  public void start() throws IOException {
    if (active) return;
    server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/stream.wav", exchange -> {
      exchange.getResponseHeaders().add("Content-Type", "audio/x-wav");
      exchange.sendResponseHeaders(200, 0); // Chunked transfer encoding

      try (OutputStream os = exchange.getResponseBody()) {
        // Simple infinite WAV-like stream (skipping header for raw PCM simplicity in some players, 
        // or add a dummy long header)
        while (active) {
          byte[] chunk = audioQueue.poll();
          if (chunk != null) {
            os.write(chunk);
            os.flush();
          } else {
            Thread.sleep(10);
          }
        }
      } catch (Exception ignored) {}
    });
    server.setExecutor(null);
    server.start();
    active = true;
    System.out.println("[Broadcaster] Started stream at http://localhost:" + port + "/stream.wav");
  }

  @Override
  protected float compute(float input, long systemTime) {
    if (!active) return input;

    // We assume stereo input for broadcasting
    float inL = (sources.size() > 0) ? sources.get(0).getChannelLastOut(0) : input;
    float inR = (sources.size() > 0) ? sources.get(0).getChannelLastOut(1) : input;

    // Convert to 16-bit PCM Little Endian
    short sL = (short) (Math.max(-1f, Math.min(1f, inL)) * 32767f);
    short sR = (short) (Math.max(-1f, Math.min(1f, inR)) * 32767f);

    pcmBuffer[pcmIdx++] = (byte) (sL & 0xFF);
    pcmBuffer[pcmIdx++] = (byte) ((sL >> 8) & 0xFF);
    pcmBuffer[pcmIdx++] = (byte) (sR & 0xFF);
    pcmBuffer[pcmIdx++] = (byte) ((sR >> 8) & 0xFF);

    if (pcmIdx >= pcmBuffer.length) {
      audioQueue.offer(pcmBuffer.clone());
      pcmIdx = 0;
      // Limit queue size to avoid memory bloat if no one is listening
      if (audioQueue.size() > 100) audioQueue.poll();
    }

    return input;
  }

  @Override
  public void close() {
    active = false;
    if (server != null) {
      server.stop(0);
    }
  }
}
